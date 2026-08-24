package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.infrastructure.ScraperException
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.SearchJobRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.repository.UserRepository

class AsyncSearchServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var scraperService: ScraperService
    private lateinit var searchService: SearchService
    private var userId: Int = 0

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        scraperService = mockk()
        searchService = SearchService(
            scraperService,
            BookRepository(dsl),
            UserLibraryRepository(dsl),
            SearchJobRepository(dsl)
        )
        userId = UserRepository(dsl).create(
            email = "seeker@example.com",
            passwordHash = "hash",
            displayName = "seeker",
            isSuperAdmin = false,
            forcePasswordChange = false
        ).id!!
    }

    @Test
    fun startSearchReturnsImmediatelyWhileScrapingContinues() = runBlocking {
        coEvery { scraperService.scrapeSearch(any(), any(), any(), any()) } coAnswers {
            delay(300)
            listOf(parsedBook())
        }

        val jobId = searchService.startSearch(userId, "lem", "pl", "epub", maxPages = 3)

        val immediateStatus = searchService.getJobStatus(jobId, userId)
        assertNotNull(immediateStatus)
        assertTrue(
            immediateStatus!!.status in listOf("queued", "scraping"),
            "expected a non-terminal status right after submission, got ${immediateStatus.status}"
        )
    }

    @Test
    fun completedJobExposesScrapedResults() = runBlocking {
        coEvery { scraperService.scrapeSearch(any(), any(), any(), any()) } returns listOf(parsedBook())

        val jobId = searchService.startSearch(userId, "lem", "pl", "epub", maxPages = 3)

        val status = awaitTerminalStatus(jobId)
        assertEquals("completed", status.status)
        assertEquals(1, status.results.size)
        assertEquals("Solaris", status.results.first().title)
    }

    @Test
    fun failedScrapeIsReportedOnTheJob() = runBlocking {
        coEvery { scraperService.scrapeSearch(any(), any(), any(), any()) } throws
            ScraperException("No working mirror available")

        val jobId = searchService.startSearch(userId, "lem", "pl", "epub", maxPages = 3)

        val status = awaitTerminalStatus(jobId)
        assertEquals("failed", status.status)
        assertEquals("No working mirror available", status.error)
    }

    private suspend fun awaitTerminalStatus(jobId: Int): SearchJobStatus =
        withTimeout(5_000) {
            while (true) {
                val status = searchService.getJobStatus(jobId, userId)!!
                if (status.status in listOf("completed", "failed")) {
                    return@withTimeout status
                }
                delay(10)
            }
            error("unreachable")
        }

    private fun parsedBook() = ParsedBookEntry(
        md5 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
        title = "Solaris",
        author = "Stanisław Lem",
        language = "pl",
        format = "epub",
        fileSize = "1.2MB",
        detailUrl = "/md5/a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
        coverUrl = "https://covers.example.com/solaris.jpg",
        publisher = "WL",
        year = "1961",
        description = "Ocean planet"
    )
}
