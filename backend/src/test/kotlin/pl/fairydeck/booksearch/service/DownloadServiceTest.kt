package pl.fairydeck.booksearch.service

import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.infrastructure.*
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.repository.DownloadSourceRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.repository.UserRepository
import java.io.File
import java.util.concurrent.CountDownLatch

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
        coEvery { solvearrClient.createSession(any()) } returns Unit
        coEvery { solvearrClient.destroySession(any()) } returns Unit

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
        // Status may be "queued" or already progressing due to async coroutine
        assertTrue(
            status.status in listOf(
                "queued",
                "fetching_fast_download",
                "fetching_detail",
                "fetching_slow_download",
                "fetching_torrent_metadata",
                "waiting_for_torrent_peers",
                "downloading_torrent_piece",
                "failed"
            ),
            "Expected initial status but got: ${status.status}"
        )
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
    fun shouldTreatFallbackProgressStagesAsActiveJobs() {
        val user = userRepository.create("fallback-states@test.com", "hash", "Fallback User", false, false)
        val md5 = "99887766554433221100998877665544"
        insertTestBook(md5)

        val fallbackStatuses = listOf(
            "fetching_fast_download",
            "switching_egress",
            "fetching_torrent_metadata",
            "waiting_for_torrent_peers",
            "downloading_torrent_piece"
        )

        fallbackStatuses.forEach { status ->
            val jobId = downloadJobRepository.create(user.id!!, md5, "epub")
            downloadJobRepository.updateProgress(jobId, status, 50)

            assertEquals(
                jobId,
                downloadJobRepository.findActiveByUserAndBook(user.id!!, md5, "epub")?.id
            )
            assertEquals(1, downloadJobRepository.cancelJob(jobId, user.id!!))
        }
    }

    @Test
    fun shouldReuseActiveJobInsteadOfCreatingDuplicateDownload() {
        val user = userRepository.create("dedupe@test.com", "hash", "Dedupe User", false, false)
        val md5 = "1234567890abcdef1234567890abcdef"
        insertTestBook(md5)
        val releaseWorker = CountDownLatch(1)
        every { mirrorService.getDownloadMirrors() } answers {
            releaseWorker.await()
            emptyList()
        }

        val firstJobId = runBlocking {
            downloadService.startDownload(user.id!!, md5)
        }
        val secondJobId = runBlocking {
            downloadService.startDownload(user.id!!, md5)
        }
        releaseWorker.countDown()

        assertEquals(firstJobId, secondJobId)
        assertEquals(
            1,
            downloadJobRepository.findAllByUserId(user.id!!).totalCount
        )
    }

    @Test
    fun shouldRotateToNextMirrorWhenSlowDownloadChallengeTimesOut() = runBlocking {
        val user = userRepository.create("rotation@test.com", "hash", "Rotation User", false, false)
        val md5 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
        insertTestBook(md5)
        val detailHtml = javaClass.classLoader
            .getResource("fixtures/annas-archive-detail-page.html")!!
            .readText()
        val slowDownloadHtml = javaClass.classLoader
            .getResource("fixtures/annas-archive-slow-download-page.html")!!
            .readText()

        every { mirrorService.getDownloadMirrors() } returns listOf(
            "https://annas-archive.gl",
            "https://annas-archive.gd"
        )
        coEvery {
            solvearrClient.fetchPageWithCookies(
                "https://annas-archive.gl/md5/$md5",
                any(),
                any()
            )
        } returns PageWithCookies(detailHtml, mapOf("detail" to "cookie"))
        coEvery {
            solvearrClient.fetchPageWithCookies(
                match { it.startsWith("https://annas-archive.gl/slow_download/") },
                30_000,
                any()
            )
        } throws ScraperException("DDoS challenge timed out")
        coEvery {
            solvearrClient.fetchPageWithCookies(
                "https://annas-archive.gd/md5/$md5",
                any(),
                any()
            )
        } returns PageWithCookies(detailHtml, mapOf("detail-gd" to "cookie"))
        coEvery {
            solvearrClient.fetchPageWithCookies(
                match { it.startsWith("https://annas-archive.gd/slow_download/") },
                30_000,
                any()
            )
        } returns PageWithCookies(
            slowDownloadHtml,
            mapOf("slow" to "cookie"),
            "FlareSolverrAgent"
        )
        coEvery {
            impersonatorHttpClient.fetchBinary(any(), any(), any())
        } returns "epub-content".toByteArray()

        val jobId = downloadService.startDownload(user.id!!, md5)
        val status = awaitTerminalStatus(jobId, user.id!!)

        assertEquals("completed", status.status)
        coVerifyOrder {
            solvearrClient.fetchPageWithCookies(
                "https://annas-archive.gd/md5/$md5",
                any(),
                any()
            )
            solvearrClient.fetchPageWithCookies(
                match { it.startsWith("https://annas-archive.gd/slow_download/") },
                30_000,
                any()
            )
        }
        coVerify(exactly = 1) { solvearrClient.createSession("booksearch-annas-downloads") }
        coVerify(exactly = 0) { solvearrClient.destroySession(any()) }
        coVerify {
            impersonatorHttpClient.fetchBinary(
                any(),
                any(),
                "FlareSolverrAgent"
            )
        }
        assertEquals(
            mapOf("detail-gd" to "cookie", "slow" to "cookie"),
            captureBinaryCookies()
        )

        File(scraperConfig.dataPath, status.filePath!!).delete()
        Unit
    }

    @Test
    fun shouldUseTorrentFallbackImmediatelyAfterDdosGuardChallenge() = runBlocking {
        val user = userRepository.create("torrent@test.com", "hash", "Torrent User", false, false)
        val md5 = "0123456789abcdef0123456789abcdef"
        insertTestBook(md5)
        val torrentFallbackClient = mockk<TorrentFallbackClient>()
        val detailHtml = javaClass.classLoader
            .getResource("fixtures/annas-archive-detail-page.html")!!
            .readText()
            .replace(
                "</body>",
                """
                    <ul>
                      <li>
                        <a href="/dyn/small_file/torrents/test.torrent">Public torrent</a>
                        file “aacid__upload_files_polish__target”
                      </li>
                    </ul>
                    </body>
                """.trimIndent()
            )
        val torrentBytes = "epub-from-torrent".toByteArray()

        downloadService = DownloadService(
            downloadJobRepository = downloadJobRepository,
            bookRepository = bookRepository,
            userLibraryRepository = userLibraryRepository,
            solvearrClient = solvearrClient,
            impersonatorHttpClient = impersonatorHttpClient,
            mirrorService = mirrorService,
            scraperConfig = scraperConfig,
            metadataService = null,
            torrentFallbackClient = torrentFallbackClient
        )

        every { mirrorService.getDownloadMirrors() } returns listOf(
            "https://annas-archive.gl",
            "https://annas-archive.gd"
        )
        coEvery {
            solvearrClient.fetchPageWithCookies(
                "https://annas-archive.gl/md5/$md5",
                any(),
                any()
            )
        } returns PageWithCookies(detailHtml, mapOf("detail" to "cookie"))
        coEvery {
            solvearrClient.fetchPageWithCookies(
                match { it.startsWith("https://annas-archive.gl/slow_download/") },
                30_000,
                any()
            )
        } throws ScraperException("DDoS challenge timed out")
        coEvery {
            torrentFallbackClient.download(
                any(),
                md5,
                "epub",
                "https://annas-archive.gl",
                any(),
                any()
            )
        } returns torrentBytes

        val jobId = downloadService.startDownload(user.id!!, md5)
        val status = awaitTerminalStatus(jobId, user.id!!)

        assertEquals("completed", status.status)
        coVerify(exactly = 1) {
            torrentFallbackClient.download(
                jobId,
                md5,
                "epub",
                "https://annas-archive.gl",
                match { it.fileLevel1 == "aacid__upload_files_polish__target" },
                any()
            )
        }
        coVerify(exactly = 0) {
            solvearrClient.fetchPageWithCookies(
                "https://annas-archive.gd/md5/$md5",
                any(),
                any()
            )
        }

        File(scraperConfig.dataPath, status.filePath!!).delete()
        Unit
    }

    @Test
    fun shouldRetryDownloadResolutionThroughConfiguredProxy() = runBlocking {
        val user = userRepository.create("proxy@test.com", "hash", "Proxy User", false, false)
        val md5 = "fedcba9876543210fedcba9876543210"
        insertTestBook(md5)
        val proxyConfig = scraperConfig.copy(
            solvearrProxyUrl = "socks5://tor:9050"
        )
        val torrentFallbackClient = mockk<TorrentFallbackClient>()
        val detailHtml = javaClass.classLoader
            .getResource("fixtures/annas-archive-detail-page.html")!!
            .readText()
            .replace(
                "</body>",
                """
                    <ul>
                      <li>
                        <a href="/dyn/small_file/torrents/proxy-test.torrent">Public torrent</a>
                        file “aacid__upload_files_polish__proxy_target”
                      </li>
                    </ul>
                    </body>
                """.trimIndent()
            )
        val torrentBytes = "epub-from-proxied-metadata".toByteArray()

        downloadService = DownloadService(
            downloadJobRepository = downloadJobRepository,
            bookRepository = bookRepository,
            userLibraryRepository = userLibraryRepository,
            solvearrClient = solvearrClient,
            impersonatorHttpClient = impersonatorHttpClient,
            mirrorService = mirrorService,
            scraperConfig = proxyConfig,
            metadataService = null,
            torrentFallbackClient = torrentFallbackClient
        )

        every { mirrorService.getDownloadMirrors() } returns
            listOf("https://annas-archive.gl")
        coEvery {
            solvearrClient.createSession("booksearch-annas-downloads", null)
        } returns Unit
        coEvery {
            solvearrClient.fetchPageWithCookies(
                "https://annas-archive.gl/md5/$md5",
                any(),
                "booksearch-annas-downloads"
            )
        } throws ScraperException("Browser verification returned a challenge page")
        coEvery {
            solvearrClient.createSession(
                "booksearch-annas-downloads-proxy",
                "socks5://tor:9050"
            )
        } returns Unit
        coEvery {
            solvearrClient.fetchPageWithCookies(
                "https://annas-archive.gl/md5/$md5",
                any(),
                "booksearch-annas-downloads-proxy"
            )
        } returns PageWithCookies(detailHtml, emptyMap())
        coEvery {
            solvearrClient.fetchPageWithCookies(
                match { it.startsWith("https://annas-archive.gl/slow_download/") },
                30_000,
                "booksearch-annas-downloads-proxy"
            )
        } throws ScraperException("DDoS challenge timed out")
        coEvery {
            torrentFallbackClient.download(
                any(),
                md5,
                "epub",
                "https://annas-archive.gl",
                any(),
                any()
            )
        } returns torrentBytes

        val jobId = downloadService.startDownload(user.id!!, md5)
        val status = awaitTerminalStatus(jobId, user.id!!)

        assertEquals("completed", status.status)
        coVerifyOrder {
            solvearrClient.createSession("booksearch-annas-downloads", null)
            solvearrClient.createSession(
                "booksearch-annas-downloads-proxy",
                "socks5://tor:9050"
            )
        }
        coVerify(exactly = 1) {
            torrentFallbackClient.download(
                jobId,
                md5,
                "epub",
                "https://annas-archive.gl",
                match { it.fileLevel1 == "aacid__upload_files_polish__proxy_target" },
                any()
            )
        }

        File(proxyConfig.dataPath, status.filePath!!).delete()
        Unit
    }

    @Test
    fun shouldUseCachedTorrentMappingWhenDetailPageIsChallenged() = runBlocking {
        val user = userRepository.create("cache@test.com", "hash", "Cache User", false, false)
        val md5 = "cafebabecafebabecafebabecafebabe"
        insertTestBook(md5)
        val proxyConfig = scraperConfig.copy(
            solvearrProxyUrl = "socks5://tor:9050"
        )
        val torrentFallbackClient = mockk<TorrentFallbackClient>()
        val sourceRepository = DownloadSourceRepository(dsl)
        val cachedLink = TorrentDownloadLink(
            torrentUrl = "/dyn/small_file/torrents/cached.torrent",
            fileLevel1 = "aacid__cached_target"
        )
        sourceRepository.upsertTorrent(
            md5,
            "https://annas-archive.gl",
            cachedLink
        )
        val torrentBytes = "epub-from-cached-source".toByteArray()

        downloadService = DownloadService(
            downloadJobRepository = downloadJobRepository,
            bookRepository = bookRepository,
            userLibraryRepository = userLibraryRepository,
            solvearrClient = solvearrClient,
            impersonatorHttpClient = impersonatorHttpClient,
            mirrorService = mirrorService,
            scraperConfig = proxyConfig,
            metadataService = null,
            torrentFallbackClient = torrentFallbackClient,
            downloadSourceRepository = sourceRepository
        )

        every { mirrorService.getDownloadMirrors() } returns
            listOf("https://annas-archive.gd")
        coEvery {
            solvearrClient.fetchPageWithCookies(
                "https://annas-archive.gd/md5/$md5",
                any(),
                "booksearch-annas-downloads"
            )
        } throws ScraperException("Browser verification returned a challenge page")
        coEvery {
            torrentFallbackClient.download(
                any(),
                md5,
                "epub",
                "https://annas-archive.gd",
                cachedLink,
                any()
            )
        } returns torrentBytes

        val jobId = downloadService.startDownload(user.id!!, md5)
        val status = awaitTerminalStatus(jobId, user.id!!)

        assertEquals("completed", status.status)
        coVerify(exactly = 1) {
            torrentFallbackClient.download(
                jobId,
                md5,
                "epub",
                "https://annas-archive.gd",
                cachedLink,
                any()
            )
        }
        coVerify(exactly = 0) {
            solvearrClient.createSession(
                "booksearch-annas-downloads-proxy",
                any()
            )
        }

        File(proxyConfig.dataPath, status.filePath!!).delete()
        Unit
    }

    @Test
    fun shouldResumeQueuedJobsAfterApplicationStartup() = runBlocking {
        val user = userRepository.create("resume@test.com", "hash", "Resume User", false, false)
        val md5 = "abcdefabcdefabcdefabcdefabcdefab"
        insertTestBook(md5)
        userLibraryRepository.add(user.id!!, md5, "epub")
        val jobId = downloadJobRepository.create(user.id!!, md5, "epub")
        every { mirrorService.getDownloadMirrors() } returns emptyList()

        downloadService.resumePendingJobs()
        val status = awaitTerminalStatus(jobId, user.id!!)

        assertEquals("failed", status.status)
        assertTrue(status.error!!.contains("No download mirror is configured"))
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

    private suspend fun awaitTerminalStatus(jobId: Int, userId: Int): DownloadJobStatus =
        withTimeout(5_000) {
            while (true) {
                val status = downloadService.getJobStatus(jobId, userId)!!
                if (status.status in listOf("completed", "failed")) {
                    return@withTimeout status
                }
                delay(10)
            }
            error("unreachable")
        }

    private fun captureBinaryCookies(): Map<String, String> {
        val cookies = slot<Map<String, String>>()
        coVerify { impersonatorHttpClient.fetchBinary(any(), capture(cookies), any()) }
        return cookies.captured
    }
}
