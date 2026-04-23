package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository

class SearchServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var bookRepository: BookRepository
    private lateinit var scraperService: ScraperService
    private lateinit var searchService: SearchService

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        bookRepository = BookRepository(dsl)
        scraperService = mockk()
        val userLibraryRepository = UserLibraryRepository(dsl)
        searchService = SearchService(scraperService, bookRepository, userLibraryRepository)
    }

    @Test
    fun upsertFromSearchInsertsNewBookAndUpdatesByMd5() {
        val book = ParsedBookEntry(
            md5 = "abc123def456abc123def456abc12345",
            title = "Test Book",
            author = "Test Author",
            language = "pl",
            format = "epub",
            fileSize = "1.2MB",
            detailUrl = "/md5/abc123def456abc123def456abc12345",
            coverUrl = "https://example.com/cover.jpg",
            publisher = "Test Publisher",
            year = "2023",
            description = "A test book"
        )

        bookRepository.upsertFromSearch(listOf(book))

        val inserted = bookRepository.findByMd5("abc123def456abc123def456abc12345")
        assertNotNull(inserted)
        assertEquals("Test Book", inserted!!.title)
        assertEquals("Test Author", inserted.author)

        val updatedBook = book.copy(title = "Updated Title", author = "Updated Author")
        bookRepository.upsertFromSearch(listOf(updatedBook))

        val updated = bookRepository.findByMd5("abc123def456abc123def456abc12345")
        assertNotNull(updated)
        assertEquals("Updated Title", updated!!.title)
        assertEquals("Updated Author", updated.author)
    }

    @Test
    fun searchAlwaysCallsScraperAndReturnsFreshResults() {
        val scraped = listOf(
            ParsedBookEntry(
                md5 = "scraped0000000000000000000000001",
                title = "Nowa Fantastyczna Ksiazka",
                author = "Scraped Author",
                language = "pl",
                format = "epub",
                fileSize = "3MB",
                detailUrl = "/md5/scraped0000000000000000000000001",
                coverUrl = "",
                publisher = "",
                year = "2024",
                description = ""
            )
        )

        coEvery { scraperService.scrapeSearch("Fantastyczna", "pl", "epub", 3) } returns scraped

        val response = kotlinx.coroutines.runBlocking { searchService.search(
            userId = 1,
            query = "Fantastyczna",
            language = "pl",
            format = "epub",
            page = 1,
            maxPages = 3
        ) }

        coVerify(exactly = 1) { scraperService.scrapeSearch("Fantastyczna", "pl", "epub", 3) }
        assertEquals(1, response.results.size)
        assertEquals("Nowa Fantastyczna Ksiazka", response.results.first().title)
    }

    @Test
    fun searchDoesNotUseCachedResults() {
        // Pre-populate the books table with a "cached" entry matching the query.
        val cached = ParsedBookEntry(
            md5 = "cached00000000000000000000000001",
            title = "Cached Book",
            author = "Cached Author",
            language = "pl",
            format = "epub",
            fileSize = "1MB",
            detailUrl = "/md5/cached00000000000000000000000001",
            coverUrl = "",
            publisher = "",
            year = "2023",
            description = ""
        )
        bookRepository.upsertFromSearch(listOf(cached))

        // Scraper returns a different, fresh result.
        val scraped = listOf(
            ParsedBookEntry(
                md5 = "fresh000000000000000000000000001",
                title = "Fresh Scrape Result",
                author = "Fresh Author",
                language = "pl",
                format = "epub",
                fileSize = "2MB",
                detailUrl = "/md5/fresh000000000000000000000000001",
                coverUrl = "",
                publisher = "",
                year = "2024",
                description = ""
            )
        )
        coEvery { scraperService.scrapeSearch("Cached", "pl", "epub", 3) } returns scraped

        val response = kotlinx.coroutines.runBlocking { searchService.search(
            userId = 1,
            query = "Cached",
            language = "pl",
            format = "epub",
            page = 1,
            maxPages = 3
        ) }

        // Even though the cached row matches the query, the service must always go through the scraper.
        coVerify(exactly = 1) { scraperService.scrapeSearch("Cached", "pl", "epub", 3) }
        assertEquals(1, response.results.size)
        assertEquals("Fresh Scrape Result", response.results.first().title)
    }
}
