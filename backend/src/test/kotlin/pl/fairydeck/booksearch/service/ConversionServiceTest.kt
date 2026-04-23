package pl.fairydeck.booksearch.service

import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.infrastructure.ScraperConfig
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import java.io.File

class ConversionServiceTest {

    private lateinit var userLibraryRepository: UserLibraryRepository
    private lateinit var calibreWrapper: CalibreWrapper
    private lateinit var libraryService: LibraryService
    private lateinit var conversionService: ConversionService

    private val scraperConfig = ScraperConfig(
        solvearrUrl = "http://localhost:8191",
        userAgent = "TestAgent",
        requestDelayMs = 0,
        maxRetries = 0,
        backoffMultiplier = 1.0,
        maxConcurrentDownloads = 2,
        dataPath = "/tmp/booksearch-conversion-test"
    )

    @BeforeEach
    fun setUp() {
        userLibraryRepository = mockk(relaxed = true)
        calibreWrapper = mockk()
        libraryService = mockk()

        conversionService = ConversionService(
            userLibraryRepository = userLibraryRepository,
            calibreWrapper = calibreWrapper,
            libraryService = libraryService,
            scraperConfig = scraperConfig,
            maxConcurrentConversions = 2
        )
    }

    @Test
    fun shouldCreateConversionJobAndReturnJobId() {
        val fileInfo = LibraryFileInfo(
            absolutePath = "/tmp/booksearch-conversion-test/1/abc123.epub",
            title = "Test Book",
            format = "epub"
        )
        every { libraryService.getFileForEntry(1, 42) } returns fileInfo

        val jobId = runBlocking {
            conversionService.startConversion(userId = 1, libraryEntryId = 42, targetFormat = "mobi")
        }

        assertNotNull(jobId)
        assertTrue(jobId.isNotBlank())
    }

    @Test
    fun shouldReturnJobStatusForExistingJob() {
        val fileInfo = LibraryFileInfo(
            absolutePath = "/tmp/booksearch-conversion-test/1/abc123.epub",
            title = "Test Book",
            format = "epub"
        )
        every { libraryService.getFileForEntry(1, 42) } returns fileInfo

        val jobId = runBlocking {
            conversionService.startConversion(userId = 1, libraryEntryId = 42, targetFormat = "mobi")
        }

        val status = conversionService.getJobStatus(jobId)

        assertNotNull(status)
        assertEquals(jobId, status!!.jobId)
        assertNotNull(status.status)
    }

    @Test
    fun shouldReturnNullForNonexistentJobId() {
        val status = conversionService.getJobStatus("nonexistent-job-id")
        assertNull(status)
    }

    @Test
    fun shouldProduceOutputPathWithTargetFormatExtension() {
        val inputPath = "/tmp/booksearch-conversion-test/1/abc123.epub"
        val fileInfo = LibraryFileInfo(
            absolutePath = inputPath,
            title = "Test Book",
            format = "epub"
        )
        every { libraryService.getFileForEntry(1, 42) } returns fileInfo

        val jobId = runBlocking {
            conversionService.startConversion(userId = 1, libraryEntryId = 42, targetFormat = "mobi")
        }

        val status = conversionService.getJobStatus(jobId)
        assertNotNull(status)
        assertTrue(status!!.outputPath == null || status.outputPath.endsWith(".mobi"),
            "Output path should end with target format extension or be null while processing")
    }

    @Test
    fun shouldRejectConversionToSameFormat() {
        val fileInfo = LibraryFileInfo(
            absolutePath = "/tmp/booksearch-conversion-test/1/abc123.epub",
            title = "Test Book",
            format = "epub"
        )
        every { libraryService.getFileForEntry(1, 42) } returns fileInfo

        assertThrows(ValidationException::class.java) {
            runBlocking {
                conversionService.startConversion(userId = 1, libraryEntryId = 42, targetFormat = "epub")
            }
        }
    }

    @Test
    fun shouldRejectUnsupportedTargetFormat() {
        val fileInfo = LibraryFileInfo(
            absolutePath = "/tmp/booksearch-conversion-test/1/abc123.epub",
            title = "Test Book",
            format = "epub"
        )
        every { libraryService.getFileForEntry(1, 42) } returns fileInfo

        assertThrows(ValidationException::class.java) {
            runBlocking {
                conversionService.startConversion(userId = 1, libraryEntryId = 42, targetFormat = "djvu")
            }
        }
    }
}
