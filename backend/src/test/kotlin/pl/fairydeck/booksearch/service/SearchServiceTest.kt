package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class SearchServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var bookRepository: BookRepository
    private lateinit var scraperService: ScraperService
    private lateinit var searchService: SearchService

    private val cacheTtlDays = 7

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        bookRepository = BookRepository(dsl)
        scraperService = mockk()
        val userLibraryRepository = UserLibraryRepository(dsl)
        searchService = SearchService(scraperService, bookRepository, userLibraryRepository, cacheTtlDays)
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
    fun findFreshByQueryReturnsCachedResultsWhenFresh() {
        val book = ParsedBookEntry(
            md5 = "fresh00000000000000000000000001",
            title = "Przestrzen objawienia",
            author = "Alastair Reynolds",
            language = "pl",
            format = "epub",
            fileSize = "2.5MB",
            detailUrl = "/md5/fresh00000000000000000000000001",
            coverUrl = "",
            publisher = "",
            year = "2020",
            description = ""
        )
        bookRepository.upsertFromSearch(listOf(book))

        val cached = bookRepository.findFreshByQuery("Przestrzen", "pl", "epub", cacheTtlDays)

        assertNotNull(cached)
        assertEquals(1, cached!!.size)
        assertEquals("Przestrzen objawienia", cached.first().title)
    }

    @Test
    fun searchCallsScraperWhenNoFreshCache() {
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
    fun searchUsesCacheWhenFresh() {
        val book = ParsedBookEntry(
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
        bookRepository.upsertFromSearch(listOf(book))

        val response = kotlinx.coroutines.runBlocking { searchService.search(
            userId = 1,
            query = "Cached",
            language = "pl",
            format = "epub",
            page = 1,
            maxPages = 3
        ) }

        coVerify(exactly = 0) { scraperService.scrapeSearch(any(), any(), any(), any()) }
        assertEquals(1, response.results.size)
        assertEquals("Cached Book", response.results.first().title)
        assertEquals("none", response.results.first().matchType)
        assertTrue(response.results.first().ownedFormats.isEmpty())
    }
}
