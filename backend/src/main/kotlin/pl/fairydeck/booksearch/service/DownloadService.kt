package pl.fairydeck.booksearch.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.infrastructure.HtmlParser
import pl.fairydeck.booksearch.infrastructure.ImpersonatorHttpClient
import pl.fairydeck.booksearch.infrastructure.PageWithCookies
import pl.fairydeck.booksearch.infrastructure.ScraperConfig
import pl.fairydeck.booksearch.infrastructure.SolvearrClient
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadService(
    private val downloadJobRepository: DownloadJobRepository,
    private val bookRepository: BookRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val solvearrClient: SolvearrClient,
    private val impersonatorHttpClient: ImpersonatorHttpClient,
    private val mirrorService: MirrorService,
    private val scraperConfig: ScraperConfig,
    private val metadataService: MetadataService? = null
) {

    private val logger = LoggerFactory.getLogger(DownloadService::class.java)
    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadSemaphore = Semaphore(scraperConfig.maxConcurrentDownloads)
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
        var sessionId: String? = null
        try {
            val mirrors = mirrorService.getWorkingMirrors()
            if (mirrors.isEmpty()) {
                throw IllegalStateException("No working mirror available")
            }

            sessionId = "booksearch-$jobId-${UUID.randomUUID()}"
            solvearrClient.createSession(sessionId)

            downloadJobRepository.updateProgress(jobId, "fetching_detail", 20)
            logger.info("Job {}: fetching detail page for {}", jobId, bookMd5)

            val (detailMirror, detailPage, downloadLinks) = fetchDetailPage(
                bookMd5,
                mirrors,
                sessionId
            )

            downloadJobRepository.updateProgress(jobId, "fetching_slow_download", 40)
            logger.info(
                "Job {}: fetching slow download page, {} links across {} mirrors",
                jobId,
                downloadLinks.size,
                mirrors.size
            )
            val (fileUrl, allCookies, userAgent) = findFileUrl(
                jobId = jobId,
                bookMd5 = bookMd5,
                detailMirror = detailMirror,
                detailPage = detailPage,
                downloadLinks = downloadLinks,
                mirrors = mirrors,
                sessionId = sessionId
            )

            downloadJobRepository.updateProgress(jobId, "downloading_file", 60)
            logger.info("Job {}: downloading file from {}", jobId, fileUrl)
            val fileBytes = impersonatorHttpClient.fetchBinary(
                fileUrl,
                allCookies,
                userAgent
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
        } finally {
            if (sessionId != null) {
                solvearrClient.destroySession(sessionId)
            }
        }
    }

    private suspend fun fetchDetailPage(
        bookMd5: String,
        mirrors: List<String>,
        sessionId: String
    ): DetailPageResult {
        var lastError: Exception? = null

        for (mirror in mirrors) {
            val detailUrl = "$mirror/md5/$bookMd5"
            try {
                val page = solvearrClient.fetchPageWithCookies(
                    detailUrl,
                    sessionId = sessionId
                )
                val links = HtmlParser.parseDetailPageDownloadLinks(page.html)
                if (links.isNotEmpty()) {
                    return DetailPageResult(mirror, page, links)
                }
                logger.warn("No download links found for {} on mirror {}", bookMd5, mirror)
            } catch (e: Exception) {
                lastError = e
                logger.warn("Detail page failed for {} on mirror {}: {}", bookMd5, mirror, e.message)
            }
        }

        throw IllegalStateException(
            buildString {
                append("Could not load a download page for $bookMd5 from ${mirrors.size} mirrors")
                lastError?.message
                    ?.takeIf { it.isNotBlank() }
                    ?.let { append(". Last error: $it") }
            },
            lastError
        )
    }

    private suspend fun findFileUrl(
        jobId: Int,
        bookMd5: String,
        detailMirror: String,
        detailPage: PageWithCookies,
        downloadLinks: List<pl.fairydeck.booksearch.infrastructure.DownloadLink>,
        mirrors: List<String>,
        sessionId: String
    ): FileUrlResult {
        val orderedMirrors = listOf(detailMirror) + mirrors.filterNot { it == detailMirror }
        val unavailableMirrors = mutableSetOf<String>()
        var lastFailure: String? = null

        for ((linkIndex, link) in downloadLinks.withIndex()) {
            for (mirror in orderedMirrors) {
                if (mirror in unavailableMirrors) continue

                val slowDownloadUrl = resolveSlowDownloadUrl(mirror, link.url)
                logger.info(
                    "Job {}: trying link {}/{} via {}",
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
                        return FileUrlResult(
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
                    unavailableMirrors += mirror
                    logger.warn(
                        "Job {}: mirror {} failed for slow downloads and will be skipped: {}",
                        jobId,
                        mirror,
                        e.message
                    )
                }
            }
        }

        throw IllegalStateException(
            buildString {
                append("A free download link is currently unavailable after trying ")
                append("${downloadLinks.size} links across ${orderedMirrors.size} mirrors.")
                if (!lastFailure.isNullOrBlank()) {
                    append(" Last error: $lastFailure")
                }
            }
        )
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

    private data class DetailPageResult(
        val mirror: String,
        val page: PageWithCookies,
        val links: List<pl.fairydeck.booksearch.infrastructure.DownloadLink>
    )

    private data class FileUrlResult(
        val url: String,
        val cookies: Map<String, String>,
        val userAgent: String
    )

    companion object {
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
