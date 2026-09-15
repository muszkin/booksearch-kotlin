package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.AnnaArchiveSessionClient
import pl.fairydeck.booksearch.infrastructure.SolvearrClient

class ScraperUrlTest {

    private lateinit var solvearrClient: SolvearrClient
    private lateinit var sessionClient: AnnaArchiveSessionClient
    private lateinit var scraperService: ScraperService

    private val searchResultsHtml: String = ScraperUrlTest::class.java.classLoader
        .getResource("fixtures/annas-archive-search-results.html")!!
        .readText()

    @BeforeEach
    fun setUp() {
        solvearrClient = mockk()
        sessionClient = mockk()
        every { sessionClient.isConfigured } returns false
        val mirrorService = mockk<MirrorService>()
        every { mirrorService.getActiveMirror() } returns "https://mirror.test"
        scraperService = ScraperService(solvearrClient, mirrorService, sessionClient)
    }

    @Test
    fun constrainsTheSearchWhenLanguageAndFormatAreGiven() = runBlocking {
        val url = captureUrlFor(language = "pl", format = "epub")

        assertTrue(url.contains("lang=pl"), "expected lang in $url")
        assertTrue(url.contains("ext=epub"), "expected ext in $url")
    }

    @Test
    fun omitsTheLanguageParameterWhenAnyIsRequested() = runBlocking {
        val url = captureUrlFor(language = "any", format = "epub")

        assertFalse(url.contains("lang="), "language must not narrow the search: $url")
        assertTrue(url.contains("ext=epub"))
    }

    @Test
    fun omitsTheFormatParameterWhenAnyIsRequested() = runBlocking {
        val url = captureUrlFor(language = "pl", format = "any")

        assertFalse(url.contains("ext="), "format must not narrow the search: $url")
        assertTrue(url.contains("lang=pl"))
    }

    private suspend fun captureUrlFor(language: String, format: String): String {
        val url = slot<String>()
        coEvery { solvearrClient.fetchPage(capture(url)) } returns searchResultsHtml
        scraperService.scrapeSearch("lem", language, format, maxPages = 1)
        return url.captured
    }
}
