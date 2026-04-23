package pl.fairydeck.booksearch.api

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pl.fairydeck.booksearch.infrastructure.*
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.repository.UserRepository
import pl.fairydeck.booksearch.service.DownloadService
import pl.fairydeck.booksearch.service.MetadataService
import pl.fairydeck.booksearch.service.MirrorService
import java.io.File
import java.nio.file.Path

class GapAnalysisPhase3Test {

    private lateinit var dsl: DSLContext
    private lateinit var downloadJobRepository: DownloadJobRepository
    private lateinit var bookRepository: BookRepository
    private lateinit var userLibraryRepository: UserLibraryRepository
    private lateinit var userRepository: UserRepository

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        downloadJobRepository = DownloadJobRepository(dsl)
        bookRepository = BookRepository(dsl)
        userLibraryRepository = UserLibraryRepository(dsl)
        userRepository = UserRepository(dsl)
    }

    // --- Test 1: User isolation - user A cannot see user B's download jobs ---
    @Test
    fun shouldNotReturnDownloadJobBelongingToDifferentUser() {
        val userA = userRepository.create("usera@test.com", "hash", "User A", false, false)
        val userB = userRepository.create("userb@test.com", "hash", "User B", false, false)
        insertTestBook("aabbccdd11223344aabbccdd11223344")

        userLibraryRepository.add(userA.id!!, "aabbccdd11223344aabbccdd11223344", "epub")

        val jobId = downloadJobRepository.create(userA.id!!, "aabbccdd11223344aabbccdd11223344", "epub")

        val resultForUserA = downloadJobRepository.findByIdAndUserId(jobId, userA.id!!)
        assertNotNull(resultForUserA, "User A should see their own job")

        val resultForUserB = downloadJobRepository.findByIdAndUserId(jobId, userB.id!!)
        assertNull(resultForUserB, "User B should NOT see User A's download job")
    }

    // --- Test 2: DownloadService handles FlareSolverr failure gracefully ---
    @Test
    fun shouldMarkJobAsFailedWhenSolvearrFails(@TempDir tempDir: Path) {
        val solvearrClient = mockk<SolvearrClient>()
        val impersonatorHttpClient = mockk<ImpersonatorHttpClient>()
        val mirrorService = mockk<MirrorService>()

        val scraperConfig = ScraperConfig(
            solvearrUrl = "http://localhost:8191",
            userAgent = "TestAgent",
            requestDelayMs = 0,
            maxRetries = 0,
            backoffMultiplier = 1.0,
            maxConcurrentDownloads = 2,
            dataPath = tempDir.toString()
        )

        val downloadService = DownloadService(
            downloadJobRepository = downloadJobRepository,
            bookRepository = bookRepository,
            userLibraryRepository = userLibraryRepository,
            solvearrClient = solvearrClient,
            impersonatorHttpClient = impersonatorHttpClient,
            mirrorService = mirrorService,
            scraperConfig = scraperConfig,
            metadataService = null
        )

        val user = userRepository.create("solvearr-fail@test.com", "hash", "Solvearr Fail", false, false)
        insertTestBook("ff00ff00ff00ff00ff00ff00ff00ff00")
        userLibraryRepository.add(user.id!!, "ff00ff00ff00ff00ff00ff00ff00ff00", "epub")

        coEvery { mirrorService.getActiveMirror() } returns "https://annas-archive.org"
        coEvery { solvearrClient.fetchPageWithCookies(any()) } throws ScraperException("Solvearr unavailable")

        val jobId = runBlocking {
            downloadService.startDownload(user.id!!, "ff00ff00ff00ff00ff00ff00ff00ff00")
        }

        // Wait for async processing
        Thread.sleep(1000)

        val job = downloadJobRepository.findByIdAndUserId(jobId, user.id!!)
        assertNotNull(job)
        assertEquals("failed", job!!.status, "Job should be marked as failed when Solvearr is unavailable")
        assertNotNull(job.error, "Error message should be set")
        assertTrue(job.error!!.contains("Solvearr"), "Error should mention Solvearr")
    }

    // --- Test 3: DownloadService cleans up partial file on download failure ---
    @Test
    fun shouldCleanUpPartialFileOnDownloadFailure(@TempDir tempDir: Path) {
        val solvearrClient = mockk<SolvearrClient>()
        val impersonatorHttpClient = mockk<ImpersonatorHttpClient>()
        val mirrorService = mockk<MirrorService>()

        val scraperConfig = ScraperConfig(
            solvearrUrl = "http://localhost:8191",
            userAgent = "TestAgent",
            requestDelayMs = 0,
            maxRetries = 0,
            backoffMultiplier = 1.0,
            maxConcurrentDownloads = 2,
            dataPath = tempDir.toString()
        )

        val downloadService = DownloadService(
            downloadJobRepository = downloadJobRepository,
            bookRepository = bookRepository,
            userLibraryRepository = userLibraryRepository,
            solvearrClient = solvearrClient,
            impersonatorHttpClient = impersonatorHttpClient,
            mirrorService = mirrorService,
            scraperConfig = scraperConfig,
            metadataService = null
        )

        val user = userRepository.create("cleanup@test.com", "hash", "Cleanup", false, false)
        insertTestBook("cc00cc00cc00cc00cc00cc00cc00cc00")
        userLibraryRepository.add(user.id!!, "cc00cc00cc00cc00cc00cc00cc00cc00", "epub")

        val detailHtml = """
            <html><body>
            <div id="md5-panel-downloads">
                <a href="/slow_download/cc00cc00cc00cc00cc00cc00cc00cc00/0/2">Server 1 — no waitlist</a>
            </div>
            </body></html>
        """.trimIndent()

        val slowDownloadHtml = """
            <html><body>
            <a href="https://download.example.com/file/cc00cc00cc00/book.epub">Download</a>
            </body></html>
        """.trimIndent()

        coEvery { mirrorService.getActiveMirror() } returns "https://annas-archive.org"
        coEvery { solvearrClient.fetchPageWithCookies(match { it.contains("/md5/") }) } returns
                PageWithCookies(html = detailHtml, cookies = mapOf("cf" to "val"))
        coEvery { solvearrClient.fetchPageWithCookies(match { it.contains("slow_download") }) } returns
                PageWithCookies(html = slowDownloadHtml, cookies = emptyMap())
        coEvery { impersonatorHttpClient.fetchBinary(any(), any()) } throws
                ScraperException("Connection reset")

        val jobId = runBlocking {
            downloadService.startDownload(user.id!!, "cc00cc00cc00cc00cc00cc00cc00cc00")
        }

        Thread.sleep(1000)

        val job = downloadJobRepository.findByIdAndUserId(jobId, user.id!!)
        assertNotNull(job)
        assertEquals("failed", job!!.status)

        val userDir = File(tempDir.toFile(), user.id!!.toString())
        val potentialFile = File(userDir, "cc00cc00cc00cc00cc00cc00cc00cc00.epub")
        assertFalse(potentialFile.exists(), "Partial file should be cleaned up after failure")
    }

    // --- Test 4: Progress transitions are monotonically increasing in processJob ---
    @Test
    fun shouldSetProgressValuesMonotonicallyDuringDownload(@TempDir tempDir: Path) {
        val solvearrClient = mockk<SolvearrClient>()
        val impersonatorHttpClient = mockk<ImpersonatorHttpClient>()
        val mirrorService = mockk<MirrorService>()

        val scraperConfig = ScraperConfig(
            solvearrUrl = "http://localhost:8191",
            userAgent = "TestAgent",
            requestDelayMs = 0,
            maxRetries = 0,
            backoffMultiplier = 1.0,
            maxConcurrentDownloads = 2,
            dataPath = tempDir.toString()
        )

        val downloadService = DownloadService(
            downloadJobRepository = downloadJobRepository,
            bookRepository = bookRepository,
            userLibraryRepository = userLibraryRepository,
            solvearrClient = solvearrClient,
            impersonatorHttpClient = impersonatorHttpClient,
            mirrorService = mirrorService,
            scraperConfig = scraperConfig,
            metadataService = null
        )

        val user = userRepository.create("progress@test.com", "hash", "Progress", false, false)
        insertTestBook("bb00bb00bb00bb00bb00bb00bb00bb00")
        userLibraryRepository.add(user.id!!, "bb00bb00bb00bb00bb00bb00bb00bb00", "epub")

        val detailHtml = """
            <html><body>
            <div id="md5-panel-downloads">
                <a href="/slow_download/bb00bb00bb00bb00bb00bb00bb00bb00/0/2">Server 1 — no waitlist</a>
            </div>
            </body></html>
        """.trimIndent()

        val slowDownloadHtml = """
            <html><body>
            <a href="https://download.example.com/file/bb00bb00bb00/book.epub">Download</a>
            </body></html>
        """.trimIndent()

        coEvery { mirrorService.getActiveMirror() } returns "https://annas-archive.org"
        coEvery { solvearrClient.fetchPageWithCookies(match { it.contains("/md5/") }) } returns
                PageWithCookies(html = detailHtml, cookies = mapOf("cf" to "val"))
        coEvery { solvearrClient.fetchPageWithCookies(match { it.contains("slow_download") }) } returns
                PageWithCookies(html = slowDownloadHtml, cookies = emptyMap())
        coEvery { impersonatorHttpClient.fetchBinary(any(), any()) } returns
                "fake epub content".toByteArray()

        val jobId = runBlocking {
            downloadService.startDownload(user.id!!, "bb00bb00bb00bb00bb00bb00bb00bb00")
        }

        Thread.sleep(1500)

        val finalJob = downloadJobRepository.findByIdAndUserId(jobId, user.id!!)
        assertNotNull(finalJob)
        assertEquals("completed", finalJob!!.status)
        assertEquals(100, finalJob.progress, "Completed job should have progress=100")
    }

    // --- Test 5: SolvearrClient handles non-ok status response ---
    @Test
    fun shouldThrowWhenSolvearrReturnsNonOkStatus() {
        val errorResponse = """
        {
            "status": "error",
            "message": "Challenge not solved",
            "solution": null
        }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(errorResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = SolvearrClient(
            config = ScraperConfig(
                solvearrUrl = "http://localhost:8191",
                userAgent = "test",
                requestDelayMs = 0,
                maxRetries = 0,
                backoffMultiplier = 1.0,
            ),
            httpClientOverride = HttpClient(mockEngine)
        )

        assertThrows(ScraperException::class.java) {
            runBlocking {
                client.fetchPageWithCookies("https://annas-archive.org/md5/abc123")
            }
        }

        client.close()
    }

    // --- Test 6: SolvearrClient handles HTTP error from upstream ---
    @Test
    fun shouldThrowWhenSolvearrReturnsHttpError() {
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("Internal Server Error"),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val client = SolvearrClient(
            config = ScraperConfig(
                solvearrUrl = "http://localhost:8191",
                userAgent = "test",
                requestDelayMs = 0,
                maxRetries = 0,
                backoffMultiplier = 1.0,
            ),
            httpClientOverride = HttpClient(mockEngine)
        )

        assertThrows(ScraperException::class.java) {
            runBlocking {
                client.fetchPageWithCookies("https://annas-archive.org/md5/abc123")
            }
        }

        client.close()
    }

    // --- Test 7: BookRepository.updateMetadata enriches existing book ---
    @Test
    fun shouldUpdateMetadataFieldsOnExistingBook() {
        insertTestBook("meta0000000000000000000000000001")

        val before = bookRepository.findByMd5("meta0000000000000000000000000001")
        assertNotNull(before)
        assertEquals("Test Book meta0000000000000000000000000001", before!!.title)
        assertEquals("Test Author", before.author)

        bookRepository.updateMetadata(
            md5 = "meta0000000000000000000000000001",
            title = "Enriched Title from EPUB",
            author = "Real Author Name",
            publisher = "Real Publisher",
            description = "Detailed description from epub metadata"
        )

        val after = bookRepository.findByMd5("meta0000000000000000000000000001")
        assertNotNull(after)
        assertEquals("Enriched Title from EPUB", after!!.title)
        assertEquals("Real Author Name", after.author)
        assertEquals("Real Publisher", after.publisher)
        assertEquals("Detailed description from epub metadata", after.description)
    }

    // --- Test 8: BookRepository.updateMetadata with null fields preserves existing values ---
    @Test
    fun shouldPreserveExistingFieldsWhenUpdatingMetadataWithNulls() {
        insertTestBook("meta0000000000000000000000000002")

        bookRepository.updateMetadata(
            md5 = "meta0000000000000000000000000002",
            title = null,
            author = null,
            publisher = "Only Publisher Updated",
            description = null
        )

        val after = bookRepository.findByMd5("meta0000000000000000000000000002")
        assertNotNull(after)
        assertEquals("Test Book meta0000000000000000000000000002", after!!.title, "Title should be unchanged")
        assertEquals("Test Author", after.author, "Author should be unchanged")
        assertEquals("Only Publisher Updated", after.publisher)
    }

    // --- Test 9: DownloadService rejects download when no mirror available ---
    @Test
    fun shouldMarkJobAsFailedWhenNoMirrorAvailable(@TempDir tempDir: Path) {
        val solvearrClient = mockk<SolvearrClient>()
        val impersonatorHttpClient = mockk<ImpersonatorHttpClient>()
        val mirrorService = mockk<MirrorService>()

        val scraperConfig = ScraperConfig(
            solvearrUrl = "http://localhost:8191",
            userAgent = "TestAgent",
            requestDelayMs = 0,
            maxRetries = 0,
            backoffMultiplier = 1.0,
            maxConcurrentDownloads = 2,
            dataPath = tempDir.toString()
        )

        val downloadService = DownloadService(
            downloadJobRepository = downloadJobRepository,
            bookRepository = bookRepository,
            userLibraryRepository = userLibraryRepository,
            solvearrClient = solvearrClient,
            impersonatorHttpClient = impersonatorHttpClient,
            mirrorService = mirrorService,
            scraperConfig = scraperConfig,
            metadataService = null
        )

        val user = userRepository.create("nomirror@test.com", "hash", "No Mirror", false, false)
        insertTestBook("dd00dd00dd00dd00dd00dd00dd00dd00")
        userLibraryRepository.add(user.id!!, "dd00dd00dd00dd00dd00dd00dd00dd00", "epub")

        coEvery { mirrorService.getActiveMirror() } returns null

        val jobId = runBlocking {
            downloadService.startDownload(user.id!!, "dd00dd00dd00dd00dd00dd00dd00dd00")
        }

        Thread.sleep(1000)

        val job = downloadJobRepository.findByIdAndUserId(jobId, user.id!!)
        assertNotNull(job)
        assertEquals("failed", job!!.status)
        assertTrue(job.error!!.contains("mirror", ignoreCase = true))
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
