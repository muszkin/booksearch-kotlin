package pl.fairydeck.booksearch.service

import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.infrastructure.HtmlParser
import pl.fairydeck.booksearch.infrastructure.ImpersonatorHttpClient
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.infrastructure.ScraperException
import pl.fairydeck.booksearch.infrastructure.SolvearrClient

class ScraperService(
    private val impersonatorHttpClient: ImpersonatorHttpClient,
    private val solvearrClient: SolvearrClient,
    private val mirrorService: MirrorService
) {

    private val logger = LoggerFactory.getLogger(ScraperService::class.java)

    suspend fun scrapeSearch(query: String, language: String, format: String, maxPages: Int): List<ParsedBookEntry> {
        val mirror = mirrorService.getActiveMirror()
            ?: throw ScraperException("No working mirror available")

        val allResults = mutableListOf<ParsedBookEntry>()

        for (page in 1..maxPages) {
            val url = buildSearchUrl(mirror, query, language, format, page)
            logger.info("Scraping page {} from: {}", page, url)

            val html = fetchWithFallback(url)
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

    private suspend fun fetchWithFallback(url: String): String {
        val result = impersonatorHttpClient.fetch(url)

        if (ImpersonatorHttpClient.isChallengePage(result.body)) {
            logger.warn("Challenge detected, falling back to Solvearr for: {}", url)
            return solvearrClient.fetchPage(url)
        }

        if (result.statusCode !in 200..399) {
            throw ScraperException("HTTP ${result.statusCode} from $url")
        }

        return result.body
    }

    private fun buildSearchUrl(mirror: String, query: String, language: String, format: String, page: Int): String {
        val encodedQuery = java.net.URLEncoder.encode(query, Charsets.UTF_8)
        return "$mirror/search?q=$encodedQuery&lang=$language&ext=$format&page=$page"
    }
}
