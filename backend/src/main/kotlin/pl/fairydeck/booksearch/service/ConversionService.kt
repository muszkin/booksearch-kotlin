package pl.fairydeck.booksearch.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.infrastructure.ScraperConfig
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ConversionService(
    private val userLibraryRepository: UserLibraryRepository,
    private val calibreWrapper: CalibreWrapper,
    private val libraryService: LibraryService,
    private val scraperConfig: ScraperConfig,
    maxConcurrentConversions: Int = 2
) {

    private val logger = LoggerFactory.getLogger(ConversionService::class.java)
    private val conversionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val conversionSemaphore = Semaphore(maxConcurrentConversions)
    private val jobs = ConcurrentHashMap<String, ConversionJobStatus>()

    companion object {
        private val SUPPORTED_FORMATS = setOf("epub", "mobi")
        private val VALID_CONVERSIONS = setOf("epub" to "mobi", "mobi" to "epub")
    }

    suspend fun startConversion(userId: Int, libraryEntryId: Int, targetFormat: String): String {
        val normalizedTarget = targetFormat.lowercase()

        val fileInfo = libraryService.getFileForEntry(userId, libraryEntryId)
        val sourceFormat = fileInfo.format.lowercase()

        validateConversion(sourceFormat, normalizedTarget)

        val jobId = UUID.randomUUID().toString()
        val inputFile = File(fileInfo.absolutePath)
        val outputFile = File(inputFile.parent, "${inputFile.nameWithoutExtension}.$normalizedTarget")

        jobs[jobId] = ConversionJobStatus(
            jobId = jobId,
            status = "queued",
            sourceFormat = sourceFormat,
            targetFormat = normalizedTarget,
            outputPath = outputFile.absolutePath,
            error = null
        )

        logger.info("Created conversion job {} for user {} entry {} ({} -> {})",
            jobId, userId, libraryEntryId, sourceFormat, normalizedTarget)

        conversionScope.launch {
            conversionSemaphore.acquire()
            try {
                processConversion(jobId, userId, libraryEntryId, fileInfo, inputFile, outputFile, normalizedTarget)
            } finally {
                conversionSemaphore.release()
            }
        }

        return jobId
    }

    fun getJobStatus(jobId: String): ConversionJobStatus? = jobs[jobId]

    private fun validateConversion(sourceFormat: String, targetFormat: String) {
        if (sourceFormat == targetFormat) {
            throw ValidationException("Cannot convert $sourceFormat to the same format")
        }

        if (!SUPPORTED_FORMATS.contains(targetFormat)) {
            throw ValidationException("Unsupported target format: $targetFormat. Supported formats: ${SUPPORTED_FORMATS.joinToString(", ")}")
        }

        if (!VALID_CONVERSIONS.contains(sourceFormat to targetFormat)) {
            throw ValidationException("Conversion from $sourceFormat to $targetFormat is not supported")
        }
    }

    private suspend fun processConversion(
        jobId: String,
        userId: Int,
        libraryEntryId: Int,
        fileInfo: LibraryFileInfo,
        inputFile: File,
        outputFile: File,
        targetFormat: String
    ) {
        try {
            jobs[jobId] = jobs[jobId]!!.copy(status = "converting")
            logger.info("Job {}: starting conversion {} -> {}", jobId, inputFile.name, outputFile.name)

            calibreWrapper.convert(inputFile, outputFile)

            if (!outputFile.exists()) {
                throw IllegalStateException("Conversion output file was not created: ${outputFile.absolutePath}")
            }

            val bookMd5 = inputFile.nameWithoutExtension
            val relativePath = "${userId}/${bookMd5}.${targetFormat}"
            userLibraryRepository.add(userId, bookMd5, targetFormat)
            userLibraryRepository.updateFilePath(userId, bookMd5, targetFormat, relativePath)

            jobs[jobId] = jobs[jobId]!!.copy(status = "completed")
            logger.info("Job {}: conversion completed successfully, output at {}", jobId, relativePath)

        } catch (e: Exception) {
            logger.error("Job {}: conversion failed - {}", jobId, e.message, e)
            jobs[jobId] = jobs[jobId]!!.copy(status = "failed", error = e.message ?: "Unknown error")
            cleanupPartialFile(outputFile)
        }
    }

    private fun cleanupPartialFile(file: File) {
        if (!file.exists()) return
        try {
            file.delete()
            logger.debug("Cleaned up partial conversion file: {}", file.absolutePath)
        } catch (e: Exception) {
            logger.warn("Failed to clean up partial file {}: {}", file.absolutePath, e.message)
        }
    }
}

@Serializable
data class ConversionJobStatus(
    val jobId: String,
    val status: String,
    val sourceFormat: String,
    val targetFormat: String,
    val outputPath: String?,
    val error: String?
)
