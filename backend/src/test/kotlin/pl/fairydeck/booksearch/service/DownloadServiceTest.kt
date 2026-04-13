package pl.fairydeck.booksearch.service

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.infrastructure.*
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.repository.UserRepository

class DownloadServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var downloadJobRepository: DownloadJobRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var userLibraryRepository: UserLibraryRepository
    private lateinit var userRepository: UserRepository
    private lateinit var solvearrClient: SolvearrClient
    private lateinit var impersonatorHttpClient: ImpersonatorHttpClient
    private lateinit var mirrorService: MirrorService
    private lateinit var downloadService: DownloadService

    private val scraperConfig = ScraperConfig(
        solvearrUrl = "http://localhost:8191",
        userAgent = "TestAgent",
        requestDelayMs = 0,
        maxRetries = 0,
        backoffMultiplier = 1.0,
        cacheTtlDays = 7,
        maxConcurrentDownloads = 2,
        dataPath = "/tmp/booksearch-test"
    )

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        downloadJobRepository = DownloadJobRepository(dsl)
        bookRepository = BookRepository(dsl)
        userLibraryRepository = UserLibraryRepository(dsl)
        userRepository = UserRepository(dsl)
        solvearrClient = mockk()
        impersonatorHttpClient = mockk()
        mirrorService = mockk()

        downloadService = DownloadService(
            downloadJobRepository = downloadJobRepository,
            bookRepository = bookRepository,
            userLibraryRepository = userLibraryRepository,
            solvearrClient = solvearrClient,
            impersonatorHttpClient = impersonatorHttpClient,
            mirrorService = mirrorService,
            scraperConfig = scraperConfig,
            metadataService = null
        )
    }

    @Test
    fun shouldCreateDownloadJobAndReturnJobId() {
        val user = userRepository.create("dl@test.com", "hash", "DL User", false, false)
        insertTestBook("aabbccdd00112233aabbccdd00112233")

        userLibraryRepository.add(user.id!!, "aabbccdd00112233aabbccdd00112233", "epub")

        val jobId = runBlocking {
            downloadService.startDownload(user.id!!, "aabbccdd00112233aabbccdd00112233")
        }

        assertTrue(jobId > 0)

        val job = downloadJobRepository.findByIdAndUserId(jobId, user.id!!)
        assertNotNull(job)
        assertEquals("queued", job!!.status)
        assertEquals(0, job.progress)
        assertEquals("aabbccdd00112233aabbccdd00112233", job.bookMd5)
    }

    @Test
    fun shouldRejectDownloadForUnknownMd5() {
        val user = userRepository.create("reject@test.com", "hash", "Reject User", false, false)

        assertThrows(NotFoundException::class.java) {
            runBlocking {
                downloadService.startDownload(user.id!!, "nonexistent000000000000000000000")
            }
        }
    }

    @Test
    fun shouldReturnJobStatusForExistingJob() {
        val user = userRepository.create("status@test.com", "hash", "Status User", false, false)
        insertTestBook("11223344556677881122334455667788")
        userLibraryRepository.add(user.id!!, "11223344556677881122334455667788", "epub")

        val jobId = runBlocking {
            downloadService.startDownload(user.id!!, "11223344556677881122334455667788")
        }

        val status = downloadService.getJobStatus(jobId, user.id!!)

        assertNotNull(status)
        assertEquals(jobId, status!!.id)
        assertEquals("queued", status.status)
        assertEquals(0, status.progress)
    }

    @Test
    fun shouldReturnNullStatusForNonExistentJob() {
        val user = userRepository.create("nostatus@test.com", "hash", "No Status User", false, false)

        val status = downloadService.getJobStatus(9999, user.id!!)
        assertNull(status)
    }

    @Test
    fun shouldCreateJobInRepositoryWithCorrectFields() {
        val user = userRepository.create("repo@test.com", "hash", "Repo User", false, false)
        insertTestBook("ffeeddccbbaa99887766554433221100")
        userLibraryRepository.add(user.id!!, "ffeeddccbbaa99887766554433221100", "pdf")

        val jobId = downloadJobRepository.create(user.id!!, "ffeeddccbbaa99887766554433221100", "pdf")

        assertTrue(jobId > 0)
        val job = downloadJobRepository.findByIdAndUserId(jobId, user.id!!)
        assertNotNull(job)
        assertEquals(user.id, job!!.userId)
        assertEquals("ffeeddccbbaa99887766554433221100", job.bookMd5)
        assertEquals("pdf", job.format)
        assertEquals("queued", job.status)
        assertEquals(0, job.progress)
        assertNotNull(job.createdAt)
    }

    @Test
    fun shouldUpdateJobProgress() {
        val user = userRepository.create("progress@test.com", "hash", "Progress User", false, false)
        insertTestBook("aabb00112233445566778899aabb0011")
        userLibraryRepository.add(user.id!!, "aabb00112233445566778899aabb0011", "epub")

        val jobId = downloadJobRepository.create(user.id!!, "aabb00112233445566778899aabb0011", "epub")

        downloadJobRepository.updateProgress(jobId, "downloading", 60)

        val updated = downloadJobRepository.findByIdAndUserId(jobId, user.id!!)
        assertNotNull(updated)
        assertEquals("downloading", updated!!.status)
        assertEquals(60, updated.progress)
    }

    private fun insertTestBook(md5: String) {
        bookRepository.upsertFromSearch(
            listOf(
                ParsedBookEntry(
                    md5 = md5,
                    title = "Test Book $md5",
                    author = "Test Author",
                    language = "pl",
                    format = "epub",
                    fileSize = "1MB",
                    detailUrl = "/md5/$md5",
                    coverUrl = "",
                    publisher = "",
                    year = "2024",
                    description = ""
                )
            )
        )
    }
}
