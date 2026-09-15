package pl.fairydeck.booksearch.service

import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.infrastructure.AnnaArchiveSessionClient
import pl.fairydeck.booksearch.infrastructure.HtmlParser
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.infrastructure.ScraperException
import pl.fairydeck.booksearch.infrastructure.SolvearrClient

class ScraperService(
    private val solvearrClient: SolvearrClient,
    private val mirrorService: MirrorService,
    private val sessionClient: AnnaArchiveSessionClient? = null,
    private val scrapeBudgetMillis: Long = DEFAULT_SCRAPE_BUDGET_MILLIS
) {

    private val logger = LoggerFactory.getLogger(ScraperService::class.java)

    suspend fun scrapeSearch(query: String, language: String, format: String, maxPages: Int): List<ParsedBookEntry> {
        val mirror = mirrorService.getActiveMirror()
            ?: throw ScraperException("No working mirror available")

        val deadline = System.currentTimeMillis() + scrapeBudgetMillis
        val allResults = mutableListOf<ParsedBookEntry>()

        for (page in 1..maxPages) {
            if (page > 1 && System.currentTimeMillis() >= deadline) {
                logger.warn(
                    "Scrape budget of {}ms exhausted after page {}, returning {} partial results",
                    scrapeBudgetMillis,
                    page - 1,
                    allResults.size
                )
                break
            }

            val url = buildSearchUrl(mirror, query, language, format, page)
            logger.info("Scraping page {} from: {}", page, url)

            val html = fetchPage(url)
            val parsed = HtmlParser.parseSearchResults(html)

            if (parsed.isEmpty()) {
                logger.info("No results on page {}, stopping pagination", page)
                break
            }

            allResults.addAll(parsed)
        }

        logger.info("Scraped {} total results for query '{}'", allResults.size, query)
        return allResults
    }

    /**
     * DDoS-Guard serves `/search` directly to a signed-in member, so the authenticated
     * plain-HTTP path costs seconds where the headless browser costs up to 90 seconds
     * per page and currently fails the challenge outright. The browser stays as a
     * fallback for deployments without a member key.
     */
    private suspend fun fetchPage(url: String): String {
        if (sessionClient?.isConfigured == true) {
            try {
                return sessionClient.fetchPage(url)
            } catch (e: Exception) {
                logger.warn("Member session fetch failed for page, falling back to browser: {}", e.message)
            }
        }
        return solvearrClient.fetchPage(url)
    }

    /**
     * Anna's Archive narrows on whatever `lang`/`ext` it is given, so the only way to get
     * a mixed result set worth filtering client-side is to leave the parameter out.
     */
    private fun buildSearchUrl(mirror: String, query: String, language: String, format: String, page: Int): String {
        val parameters = buildList {
            add("q=" + java.net.URLEncoder.encode(query, Charsets.UTF_8))
            if (!language.isAny()) add("lang=$language")
            if (!format.isAny()) add("ext=$format")
            add("page=$page")
        }
        return "$mirror/search?" + parameters.joinToString("&")
    }

    private fun String.isAny(): Boolean = equals(ANY_VALUE, ignoreCase = true)

    companion object {
        const val ANY_VALUE = "any"
        const val DEFAULT_SCRAPE_BUDGET_MILLIS = 5L * 60 * 1000
    }
}
