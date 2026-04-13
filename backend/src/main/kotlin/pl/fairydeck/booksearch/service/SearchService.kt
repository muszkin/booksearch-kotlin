package pl.fairydeck.booksearch.service

import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.jooq.generated.tables.records.BooksRecord
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository

class SearchService(
    private val scraperService: ScraperService,
    private val bookRepository: BookRepository,
    private val userLibraryRepository: UserLibraryRepository,
    private val cacheTtlDays: Int
) {

    private val logger = LoggerFactory.getLogger(SearchService::class.java)

    suspend fun search(userId: Int, query: String, language: String, format: String, page: Int, maxPages: Int): SearchResponse {
        val escapedQuery = escapeLikeWildcards(query)
        val cachedBooks = bookRepository.findFreshByQuery(escapedQuery, language, format, cacheTtlDays)

        val books = if (cachedBooks != null) {
            logger.info("Cache hit for query '{}' ({} results)", query, cachedBooks.size)
            cachedBooks
        } else {
            logger.info("Cache miss for query '{}', scraping...", query)
            val scraped = scraperService.scrapeSearch(query, language, format, maxPages)
            bookRepository.upsertFromSearch(scraped)
            bookRepository.findFreshByQuery(escapedQuery, language, format, cacheTtlDays) ?: emptyList()
        }

        val enriched = enrichWithOwnership(userId, books)

        return SearchResponse(
            results = enriched,
            totalResults = enriched.size,
            query = query
        )
    }

    private fun enrichWithOwnership(userId: Int, books: List<BooksRecord>): List<BookResult> {
        val md5s = books.mapNotNull { it.md5 }
        val exactEntries = userLibraryRepository.findByUserAndBookMd5s(userId, md5s)
        val ownedByMd5 = exactEntries.groupBy { it.bookMd5 ?: "" }

        val titles = books.mapNotNull { it.title?.lowercase()?.trim() }.distinct()
        val titleEntries = userLibraryRepository.findByUserAndTitles(userId, titles)
        val ownedTitles = titleEntries.mapNotNull { record ->
            val bookMd5 = record.bookMd5
            if (bookMd5 != null) {
                val book = books.find { false }
                null
            } else null
        }.toSet()

        val titleSet = titleEntries.flatMap { record ->
            val bmd5 = record.bookMd5 ?: return@flatMap emptyList()
            val bookRecord = bookRepository.findByMd5(bmd5)
            listOfNotNull(bookRecord?.title?.lowercase()?.trim())
        }.toSet()

        val authors = books.mapNotNull { it.author?.lowercase()?.trim() }.filter { it.isNotBlank() }.distinct()
        val authorEntries = userLibraryRepository.findByUserAndAuthors(userId, authors)
        val authorSet = authorEntries.flatMap { record ->
            val bmd5 = record.bookMd5 ?: return@flatMap emptyList()
            val bookRecord = bookRepository.findByMd5(bmd5)
            listOfNotNull(bookRecord?.author?.lowercase()?.trim())
        }.toSet()

        return books.map { book ->
            val owned = ownedByMd5[book.md5]
            if (owned != null) {
                val formats = owned.mapNotNull { it.format }
                book.toBookResult("exact", formats)
            } else if (book.title?.lowercase()?.trim() in titleSet) {
                book.toBookResult("title", emptyList())
            } else if (book.author?.lowercase()?.trim() in authorSet) {
                book.toBookResult("author", emptyList())
            } else {
                book.toBookResult("none", emptyList())
            }
        }
    }

    private fun escapeLikeWildcards(query: String): String =
        query.replace("%", "\\%").replace("_", "\\_")

    private fun BooksRecord.toBookResult(matchType: String, ownedFormats: List<String>): BookResult =
        BookResult(
            md5 = md5 ?: "",
            title = title ?: "",
            author = author ?: "",
            language = language ?: "",
            format = format ?: "",
            fileSize = fileSize ?: "",
            detailUrl = detailUrl ?: "",
            coverUrl = coverUrl ?: "",
            publisher = publisher ?: "",
            year = year ?: "",
            description = description ?: "",
            matchType = matchType,
            ownedFormats = ownedFormats
        )
}

@Serializable
data class SearchResponse(
    val results: List<BookResult>,
    val totalResults: Int,
    val query: String
)

@Serializable
data class BookResult(
    val md5: String,
    val title: String,
    val author: String,
    val language: String,
    val format: String,
    val fileSize: String,
    val detailUrl: String,
    val coverUrl: String,
    val publisher: String,
    val year: String,
    val description: String,
    val matchType: String,
    val ownedFormats: List<String>
)
