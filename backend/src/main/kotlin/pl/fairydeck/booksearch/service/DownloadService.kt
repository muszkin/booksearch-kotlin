package pl.fairydeck.booksearch.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.infrastructure.HtmlParser
import pl.fairydeck.booksearch.infrastructure.ImpersonatorHttpClient
import pl.fairydeck.booksearch.infrastructure.AnnaArchiveFastDownloadClient
import pl.fairydeck.booksearch.infrastructure.PageWithCookies
import pl.fairydeck.booksearch.infrastructure.ScraperConfig
import pl.fairydeck.booksearch.infrastructure.ScraperException
import pl.fairydeck.booksearch.infrastructure.SolvearrClient
import pl.fairydeck.booksearch.infrastructure.TorrentDownloadLink
import pl.fairydeck.booksearch.infrastructure.TorrentFallbackClient
import pl.fairydeck.booksearch.infrastructure.TorrentProgress
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.repository.DownloadSourceRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class DownloadService(
    private val downloadJobRepository: DownloadJobRepository,
    private val bookRepository: BookRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val solvearrClient: SolvearrClient,
    private val impersonatorHttpClient: ImpersonatorHttpClient,
    private val mirrorService: MirrorService,
    private val scraperConfig: ScraperConfig,
    private val metadataService: MetadataService? = null,
    private val fastDownloadClient: AnnaArchiveFastDownloadClient? = null,
    private val torrentFallbackClient: TorrentFallbackClient? = null,
    private val downloadSourceRepository: DownloadSourceRepository? = null
) {

    private val logger = LoggerFactory.getLogger(DownloadService::class.java)
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadSemaphore = Semaphore(scraperConfig.maxConcurrentDownloads)
    private val downloadResolutionMutex = Mutex()
    private val scheduledJobs = ConcurrentHashMap.newKeySet<Int>()

    suspend fun startDownload(userId: Int, bookMd5: String): Int {
        val book = bookRepository.findByMd5(bookMd5)
            ?: throw NotFoundException("Book not found with md5: $bookMd5")

        val format = book.format ?: "epub"
        userLibraryRepository.findOrCreate(userId, bookMd5, format)
        val activeJob = downloadJobRepository.findActiveByUserAndBook(userId, bookMd5, format)
        if (activeJob != null) {
            scheduleJob(activeJob.id!!, userId, bookMd5, format)
            logger.info(
                "Reusing active download job {} for user {} book {}",
                activeJob.id,
                userId,
                bookMd5
            )
            return activeJob.id!!
        }

        val jobId = downloadJobRepository.create(userId, bookMd5, format)

        logger.info("Created download job {} for user {} book {}", jobId, userId, bookMd5)
        scheduleJob(jobId, userId, bookMd5, format)

        return jobId
    }

    fun resumePendingJobs() {
        val jobs = downloadJobRepository.findRecoverable()
        if (jobs.isNotEmpty()) {
            logger.info("Resuming {} pending download jobs after startup", jobs.size)
        }

        for (job in jobs) {
            scheduleJob(
                jobId = job.id!!,
                userId = job.userId!!,
                bookMd5 = job.bookMd5!!,
                format = job.format!!
            )
        }
    }

    private fun scheduleJob(jobId: Int, userId: Int, bookMd5: String, format: String) {
        if (!scheduledJobs.add(jobId)) return

        downloadScope.launch {
            downloadSemaphore.acquire()
            try {
                processJob(jobId, userId, bookMd5, format)
            } finally {
                downloadSemaphore.release()
                scheduledJobs.remove(jobId)
            }
        }
    }

    fun getJobStatus(jobId: Int, userId: Int): DownloadJobStatus? {
        val job = downloadJobRepository.findByIdAndUserId(jobId, userId) ?: return null
        return DownloadJobStatus(
            id = job.id!!,
            bookMd5 = job.bookMd5!!,
            format = job.format!!,
            status = job.status!!,
            progress = job.progress!!,
            filePath = job.filePath,
            error = job.error,
            createdAt = job.createdAt!!,
            updatedAt = job.updatedAt!!
        )
    }

    private suspend fun processJob(jobId: Int, userId: Int, bookMd5: String, format: String) {
        var targetFile: File? = null
        try {
            val mirrors = mirrorService.getDownloadMirrors()
            if (mirrors.isEmpty()) {
                throw IllegalStateException("No download mirror is configured")
            }

            val fileBytes = downloadBook(
                bookMd5 = bookMd5,
                mirrors = mirrors,
                jobId = jobId,
                format = format
            )

            val userDir = File(scraperConfig.dataPath, userId.toString())
            userDir.mkdirs()
            targetFile = File(userDir, "$bookMd5.$format")
            targetFile.writeBytes(fileBytes)

            downloadJobRepository.updateProgress(jobId, "extracting_metadata", 80)
            logger.info("Job {}: file saved ({} bytes), extracting metadata", jobId, fileBytes.size)

            extractMetadataIfAvailable(bookMd5, targetFile)

            val relativePath = "${userId}/${bookMd5}.${format}"
            userLibraryRepository.updateFilePath(userId, bookMd5, format, relativePath)
            downloadJobRepository.markCompleted(jobId, relativePath)

            logger.info("Job {}: completed successfully, file at {}", jobId, relativePath)

        } catch (e: Exception) {
            logger.error("Job {}: failed - {}", jobId, e.message, e)
            cleanupPartialFile(targetFile)
            downloadJobRepository.markFailed(jobId, e.message ?: "Unknown error")
        }
    }

    private suspend fun downloadBook(
        jobId: Int,
        bookMd5: String,
        mirrors: List<String>,
        format: String
    ): ByteArray {
        val fastDownload = resolveFastDownload(jobId, bookMd5, mirrors)
        if (fastDownload != null) {
            try {
                downloadJobRepository.updateProgress(jobId, "downloading_fast_download", 30)
                logger.info("Job {}: downloading through Anna's Archive JSON API", jobId)
                val bytes = impersonatorHttpClient.fetchBinary(
                    fastDownload.url,
                    emptyMap(),
                    scraperConfig.userAgent
                )
                verifyFastDownloadChecksum(bookMd5, bytes)
                return bytes
            } catch (error: Exception) {
                logger.warn(
                    "Job {}: JSON API transfer failed ({}); falling back to the browser flow",
                    jobId,
                    error.javaClass.simpleName
                )
            }
        }

        val source = resolveLegacyDownloadSource(jobId, bookMd5, mirrors)
        return downloadResolvedSource(jobId, bookMd5, format, source)
    }

    private suspend fun resolveFastDownload(
        jobId: Int,
        bookMd5: String,
        mirrors: List<String>
    ) = if (fastDownloadClient != null && scraperConfig.annaArchiveApiKey != null) {
        downloadJobRepository.updateProgress(jobId, "fetching_fast_download", 10)
        fastDownloadClient.resolveDownload(bookMd5, mirrors)
    } else {
        null
    }

    private suspend fun resolveLegacyDownloadSource(
        jobId: Int,
        bookMd5: String,
        mirrors: List<String>
    ): DownloadSource = downloadResolutionMutex.withLock {
        try {
            solvearrClient.createSession(DOWNLOAD_SESSION_ID)
            resolveFreeDownloadSource(jobId, bookMd5, mirrors, DOWNLOAD_SESSION_ID)
        } catch (error: Exception) {
            val proxyUrl = scraperConfig.solvearrProxyUrl
            if (proxyUrl == null || !isChallengeFailure(error)) {
                throw error
            }

            logger.warn(
                "Job {}: all direct attempts were challenged; switching browser egress",
                jobId
            )
            downloadJobRepository.updateProgress(jobId, "switching_egress", 20)
            solvearrClient.createSession(DOWNLOAD_PROXY_SESSION_ID, proxyUrl)
            resolveFreeDownloadSource(
                jobId,
                bookMd5,
                mirrors,
                DOWNLOAD_PROXY_SESSION_ID
            )
        }
    }

    private suspend fun downloadResolvedSource(
        jobId: Int,
        bookMd5: String,
        format: String,
        source: DownloadSource
    ): ByteArray = when (source) {
        is DownloadSource.Direct -> {
            downloadJobRepository.updateProgress(jobId, "downloading_file", 60)
            logger.info("Job {}: downloading resolved file through the browser flow", jobId)
            impersonatorHttpClient.fetchBinary(
                source.url,
                source.cookies,
                source.userAgent
            )
        }
        is DownloadSource.Torrent -> {
            val fallbackClient = torrentFallbackClient
                ?: throw IllegalStateException("Torrent fallback is not available")
            fallbackClient.download(
                jobId = jobId,
                bookMd5 = bookMd5,
                format = format,
                mirror = source.mirror,
                link = source.link
            ) { progress ->
                updateTorrentProgress(jobId, progress)
            }.also {
                downloadJobRepository.updateProgress(jobId, "downloading_file", 60)
            }
        }
    }

    private fun verifyFastDownloadChecksum(bookMd5: String, bytes: ByteArray) {
        val checksum = MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        if (!checksum.equals(bookMd5, ignoreCase = true)) {
            throw IllegalStateException("Fast download checksum mismatch")
        }
    }

    private suspend fun resolveFreeDownloadSource(
        jobId: Int,
        bookMd5: String,
        mirrors: List<String>,
        sessionId: String
    ): DownloadSource {
        var torrentSource = downloadSourceRepository
            ?.findTorrent(bookMd5)
            ?.takeIf { !it.link.isPacked }
            ?.let { cached ->
                logger.info("Job {}: found cached public torrent mapping", jobId)
                DownloadSource.Torrent(
                    mirror = mirrors.firstOrNull() ?: cached.mirror,
                    link = cached.link
                )
            }
        var lastFailure: String? = null

        for (mirror in mirrors) {
            downloadJobRepository.updateProgress(jobId, "fetching_detail", 20)
            logger.info("Job {}: warming mirror {} for {}", jobId, mirror, bookMd5)
            val detailPage = try {
                solvearrClient.fetchPageWithCookies(
                    "$mirror/md5/$bookMd5",
                    sessionId = sessionId
                )
            } catch (e: Exception) {
                lastFailure = e.message
                logger.warn("Job {}: detail page failed on {}: {}", jobId, mirror, e.message)
                if (torrentSource != null && isChallengeFailure(e)) {
                    logger.info(
                        "Job {}: using cached torrent mapping after direct detail challenge",
                        jobId
                    )
                    return torrentSource
                }
                continue
            }
            val downloadLinks = HtmlParser.parseDetailPageDownloadLinks(detailPage.html)
            if (torrentFallbackClient != null) {
                val parsedTorrent = HtmlParser.parseTorrentDownloadLinks(detailPage.html)
                    .firstOrNull { !it.isPacked }
                if (parsedTorrent != null) {
                    downloadSourceRepository?.upsertTorrent(bookMd5, mirror, parsedTorrent)
                    torrentSource = DownloadSource.Torrent(mirror, parsedTorrent)
                }
            }

            if (downloadLinks.isEmpty()) {
                logger.warn("Job {}: no slow-download links found on {}", jobId, mirror)
                continue
            }

            downloadJobRepository.updateProgress(jobId, "fetching_slow_download", 40)
            for ((linkIndex, link) in downloadLinks.withIndex()) {
                val slowDownloadUrl = resolveSlowDownloadUrl(mirror, link.url)
                logger.info(
                    "Job {}: trying slow-download link {}/{} via warmed mirror {}",
                    jobId,
                    linkIndex + 1,
                    downloadLinks.size,
                    mirror
                )

                try {
                    val slowDownloadPage = solvearrClient.fetchPageWithCookies(
                        slowDownloadUrl,
                        SLOW_DOWNLOAD_TIMEOUT_MS,
                        sessionId
                    )
                    val resolvedPage = waitForDownloadSlotIfNeeded(
                        jobId = jobId,
                        slowDownloadUrl = slowDownloadUrl,
                        initialPage = slowDownloadPage,
                        sessionId = sessionId
                    )
                    val foundUrl = HtmlParser.parseSlowDownloadPageFileUrl(
                        resolvedPage.html,
                        bookMd5
                    )
                    if (foundUrl != null) {
                        logger.info(
                            "Job {}: found file URL on link {} via {}: {}",
                            jobId,
                            linkIndex + 1,
                            mirror,
                            foundUrl.take(80)
                        )
                        return DownloadSource.Direct(
                            url = foundUrl,
                            cookies = detailPage.cookies + resolvedPage.cookies,
                            userAgent = resolvedPage.userAgent.ifBlank {
                                detailPage.userAgent
                            }
                        )
                    }
                    logger.warn(
                        "Job {}: no file URL on link {} via {}, trying next candidate",
                        jobId,
                        linkIndex + 1,
                        mirror
                    )
                } catch (e: Exception) {
                    lastFailure = e.message
                    logger.warn(
                        "Job {}: slow downloads failed on {}: {}",
                        jobId,
                        mirror,
                        e.message
                    )
                    if (torrentSource != null && isChallengeFailure(e)) {
                        logger.info(
                            "Job {}: switching directly to torrent fallback after DDoS challenge",
                            jobId
                        )
                        return torrentSource
                    }
                    break
                }
            }
        }

        if (torrentSource != null) return torrentSource

        throw IllegalStateException(
            buildString {
                append("A free download link is currently unavailable after trying ")
                append("${mirrors.size} mirrors.")
                if (!lastFailure.isNullOrBlank()) {
                    append(" Last error: $lastFailure")
                }
            }
        )
    }

    private fun updateTorrentProgress(jobId: Int, progress: TorrentProgress) {
        when (progress) {
            TorrentProgress.FetchingMetadata ->
                downloadJobRepository.updateProgress(jobId, "fetching_torrent_metadata", 45)
            TorrentProgress.WaitingForPeers ->
                downloadJobRepository.updateProgress(jobId, "waiting_for_torrent_peers", 50)
            is TorrentProgress.Downloading -> {
                val mappedProgress = 50 + (progress.percent.coerceIn(0, 100) / 10)
                downloadJobRepository.updateProgress(
                    jobId,
                    "downloading_torrent_piece",
                    mappedProgress
                )
            }
        }
    }

    private fun isChallengeFailure(error: Exception): Boolean {
        val message = generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
        return message.contains("challenge", ignoreCase = true) ||
            message.contains("browser verification", ignoreCase = true) ||
            error is ScraperException
    }

    private suspend fun waitForDownloadSlotIfNeeded(
        jobId: Int,
        slowDownloadUrl: String,
        initialPage: PageWithCookies,
        sessionId: String
    ): PageWithCookies {
        var page = initialPage

        repeat(MAX_DOWNLOAD_SLOT_ATTEMPTS) { attempt ->
            val waitSeconds = HtmlParser.parseSlowDownloadWaitSeconds(page.html)
                ?: return page
            if (waitSeconds > MAX_DOWNLOAD_SLOT_WAIT_SECONDS) {
                throw IllegalStateException(
                    "Free download wait time is too long (${waitSeconds}s)"
                )
            }

            downloadJobRepository.updateProgress(jobId, "waiting_for_download_slot", 45)
            logger.info(
                "Job {}: waiting {} seconds for a free download slot (attempt {}/{})",
                jobId,
                waitSeconds,
                attempt + 1,
                MAX_DOWNLOAD_SLOT_ATTEMPTS
            )
            kotlinx.coroutines.delay((waitSeconds + DOWNLOAD_SLOT_GRACE_SECONDS) * 1_000L)
            downloadJobRepository.updateProgress(jobId, "fetching_slow_download", 40)
            page = solvearrClient.fetchPageWithCookies(
                slowDownloadUrl,
                SLOW_DOWNLOAD_TIMEOUT_MS,
                sessionId
            )
        }

        if (HtmlParser.parseSlowDownloadWaitSeconds(page.html) != null) {
            throw IllegalStateException("Free download slot did not become available in time")
        }
        return page
    }

    private fun resolveSlowDownloadUrl(mirror: String, linkUrl: String): String {
        if (!linkUrl.startsWith("http")) return "$mirror$linkUrl"

        val uri = URI(linkUrl)
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return "$mirror${uri.rawPath}$query"
    }

    private fun extractMetadataIfAvailable(bookMd5: String, file: File) {
        if (metadataService == null) {
            logger.debug("MetadataService not available, skipping metadata extraction for {}", bookMd5)
            return
        }

        try {
            val metadata = metadataService.extractMetadata(file.toPath())
            bookRepository.updateMetadata(
                md5 = bookMd5,
                title = metadata.title.takeIf { it.isNotBlank() },
                author = metadata.author.takeIf { it.isNotBlank() },
                publisher = metadata.publisher.takeIf { it.isNotBlank() },
                description = metadata.description.takeIf { it.isNotBlank() }
            )

            if (metadata.coverBytes != null) {
                metadataService.saveCoverImage(metadata.coverBytes, file.parentFile, bookMd5)
            }

            logger.info("Metadata extracted for {}: title='{}', author='{}'", bookMd5, metadata.title, metadata.author)
        } catch (e: Exception) {
            logger.warn("Failed to extract metadata for {}: {}", bookMd5, e.message)
        }
    }

    private fun cleanupPartialFile(file: File?) {
        if (file == null || !file.exists()) return
        try {
            file.delete()
            logger.debug("Cleaned up partial file: {}", file.absolutePath)
        } catch (e: Exception) {
            logger.warn("Failed to clean up partial file {}: {}", file.absolutePath, e.message)
        }
    }

    private sealed interface DownloadSource {
        data class Direct(
            val url: String,
            val cookies: Map<String, String>,
            val userAgent: String
        ) : DownloadSource

        data class Torrent(
            val mirror: String,
            val link: TorrentDownloadLink
        ) : DownloadSource
    }

    companion object {
        private const val DOWNLOAD_SESSION_ID = "booksearch-annas-downloads"
        private const val DOWNLOAD_PROXY_SESSION_ID = "booksearch-annas-downloads-proxy"
        private const val SLOW_DOWNLOAD_TIMEOUT_MS = 30_000
        private const val MAX_DOWNLOAD_SLOT_ATTEMPTS = 2
        private const val MAX_DOWNLOAD_SLOT_WAIT_SECONDS = 10 * 60
        private const val DOWNLOAD_SLOT_GRACE_SECONDS = 2
    }
}

@Serializable
data class DownloadJobStatus(
    val id: Int,
    val bookMd5: String,
    val format: String,
    val status: String,
    val progress: Int,
    val filePath: String?,
    val error: String?,
    val createdAt: String,
    val updatedAt: String
)
