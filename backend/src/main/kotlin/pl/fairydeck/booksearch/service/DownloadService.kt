package pl.fairydeck.booksearch.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.infrastructure.HtmlParser
import pl.fairydeck.booksearch.infrastructure.ImpersonatorHttpClient
import pl.fairydeck.booksearch.infrastructure.ScraperConfig
import pl.fairydeck.booksearch.infrastructure.SolvearrClient
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import kotlinx.serialization.Serializable
import java.io.File

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

    suspend fun startDownload(userId: Int, bookMd5: String): Int {
        val book = bookRepository.findByMd5(bookMd5)
            ?: throw NotFoundException("Book not found with md5: $bookMd5")

        val format = book.format ?: "epub"
        userLibraryRepository.findOrCreate(userId, bookMd5, format)
        val jobId = downloadJobRepository.create(userId, bookMd5, format)

        logger.info("Created download job {} for user {} book {}", jobId, userId, bookMd5)

        downloadScope.launch {
            downloadSemaphore.acquire()
            try {
                processJob(jobId, userId, bookMd5, format)
            } finally {
                downloadSemaphore.release()
            }
        }

        return jobId
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
            val mirror = mirrorService.getActiveMirror()
                ?: throw IllegalStateException("No working mirror available")

            downloadJobRepository.updateProgress(jobId, "fetching_detail", 20)
            logger.info("Job {}: fetching detail page for {}", jobId, bookMd5)

            val detailUrl = "$mirror/md5/$bookMd5"
            val detailPage = solvearrClient.fetchPageWithCookies(detailUrl)
            val downloadLinks = HtmlParser.parseDetailPageDownloadLinks(detailPage.html)

            if (downloadLinks.isEmpty()) {
                throw IllegalStateException("No download links found on detail page for $bookMd5")
            }

            downloadJobRepository.updateProgress(jobId, "fetching_slow_download", 40)
            logger.info("Job {}: fetching slow download page, {} links available", jobId, downloadLinks.size)

            var fileUrl: String? = null
            var allCookies = detailPage.cookies
            for ((index, link) in downloadLinks.withIndex()) {
                val slowDownloadUrl = resolveSlowDownloadUrl(mirror, link.url)
                logger.info("Job {}: trying slow download link {}/{}: {}", jobId, index + 1, downloadLinks.size, slowDownloadUrl)
                try {
                    val slowDownloadPage = solvearrClient.fetchPageWithCookies(slowDownloadUrl)
                    val foundUrl = HtmlParser.parseSlowDownloadPageFileUrl(slowDownloadPage.html, bookMd5)
                    if (foundUrl != null) {
                        fileUrl = foundUrl
                        allCookies = detailPage.cookies + slowDownloadPage.cookies
                        logger.info("Job {}: found file URL on link {}: {}", jobId, index + 1, foundUrl.take(80))
                        break
                    }
                    logger.warn("Job {}: no file URL on link {}, trying next...", jobId, index + 1)
                } catch (e: Exception) {
                    logger.warn("Job {}: link {} failed: {}", jobId, index + 1, e.message)
                }
            }

            if (fileUrl == null) {
                throw IllegalStateException("Could not find direct file URL on any slow download page for $bookMd5 (tried ${downloadLinks.size} links)")
            }

            downloadJobRepository.updateProgress(jobId, "downloading_file", 60)
            logger.info("Job {}: downloading file from {}", jobId, fileUrl)
            val fileBytes = impersonatorHttpClient.fetchBinary(fileUrl, allCookies)

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

    private fun resolveSlowDownloadUrl(mirror: String, linkUrl: String): String {
        if (linkUrl.startsWith("http")) return linkUrl
        return "$mirror$linkUrl"
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
