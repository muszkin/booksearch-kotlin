package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.AnnaArchiveSessionClient
import pl.fairydeck.booksearch.infrastructure.ScraperException
import pl.fairydeck.booksearch.infrastructure.SolvearrClient

class ScraperServiceTest {

    private lateinit var solvearrClient: SolvearrClient
    private lateinit var sessionClient: AnnaArchiveSessionClient
    private lateinit var mirrorService: MirrorService

    private val searchResultsHtml: String = ScraperServiceTest::class.java.classLoader
        .getResource("fixtures/annas-archive-search-results.html")!!
        .readText()

    @BeforeEach
    fun setUp() {
        solvearrClient = mockk()
        sessionClient = mockk()
        every { sessionClient.isConfigured } returns false
        mirrorService = mockk()
        every { mirrorService.getActiveMirror() } returns "https://mirror.test"
    }

    @Test
    fun stopsPaginatingOnceScrapeBudgetIsExhausted() = runBlocking {
        coEvery { solvearrClient.fetchPage(any()) } coAnswers {
            delay(PAGE_DURATION_MS)
            searchResultsHtml
        }
        val scraperService = ScraperService(solvearrClient, mirrorService, sessionClient, SHORT_BUDGET_MS)

        val results = scraperService.scrapeSearch("lem", "pl", "epub", maxPages = 3)

        coVerify(exactly = 1) { solvearrClient.fetchPage(any()) }
        assertTrue(results.isNotEmpty(), "partial results from the first page must be returned")
    }

    @Test
    fun scrapesEveryRequestedPageWhenBudgetAllows() = runBlocking {
        coEvery { solvearrClient.fetchPage(any()) } returns searchResultsHtml
        val scraperService = ScraperService(solvearrClient, mirrorService, sessionClient, GENEROUS_BUDGET_MS)

        scraperService.scrapeSearch("lem", "pl", "epub", maxPages = 3)

        coVerify(exactly = 3) { solvearrClient.fetchPage(any()) }
    }

    @Test
    fun prefersTheMemberSessionOverTheHeadlessBrowser() = runBlocking {
        every { sessionClient.isConfigured } returns true
        coEvery { sessionClient.fetchPage(any()) } returns searchResultsHtml
        val scraperService = ScraperService(solvearrClient, mirrorService, sessionClient, GENEROUS_BUDGET_MS)

        val results = scraperService.scrapeSearch("lem", "pl", "epub", maxPages = 1)

        assertTrue(results.isNotEmpty())
        coVerify(exactly = 1) { sessionClient.fetchPage(any()) }
        coVerify(exactly = 0) { solvearrClient.fetchPage(any()) }
    }

    @Test
    fun fallsBackToTheHeadlessBrowserWhenTheSessionFails() = runBlocking {
        every { sessionClient.isConfigured } returns true
        coEvery { sessionClient.fetchPage(any()) } throws ScraperException("session rejected")
        coEvery { solvearrClient.fetchPage(any()) } returns searchResultsHtml
        val scraperService = ScraperService(solvearrClient, mirrorService, sessionClient, GENEROUS_BUDGET_MS)

        val results = scraperService.scrapeSearch("lem", "pl", "epub", maxPages = 1)

        assertTrue(results.isNotEmpty())
        coVerify(exactly = 1) { solvearrClient.fetchPage(any()) }
    }

    private companion object {
        const val PAGE_DURATION_MS = 150L
        const val SHORT_BUDGET_MS = 100L
        const val GENEROUS_BUDGET_MS = 30_000L
    }
}
