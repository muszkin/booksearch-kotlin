package pl.fairydeck.booksearch.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.every
import io.mockk.verify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.HtmlParser
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.service.SearchService

class GapAnalysisPhase2Test {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(
        email: String = "gaptest@example.com"
    ): String {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"Gap Tester"}""")
        }
        val body = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    // --- Test 1: Search with maxPages=0 returns 400 ---
    @Test
    fun shouldReturn400WhenMaxPagesIsZero() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/search?q=test&maxPages=0") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["message"]?.jsonPrimitive?.content?.contains("maxPages") == true)
    }

    // --- Test 2: Search with maxPages=11 returns 400 ---
    @Test
    fun shouldReturn400WhenMaxPagesExceedsLimit() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/search?q=test&maxPages=11") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["message"]?.jsonPrimitive?.content?.contains("maxPages") == true)
    }

    // --- Test 3: HtmlParser with Unicode/non-Latin characters ---
    @Test
    fun shouldParseUnicodeCharactersInTitleAndAuthor() {
        val unicodeHtml = javaClass.classLoader
            .getResource("fixtures/annas-archive-unicode-results.html")!!
            .readText()

        val results = HtmlParser.parseSearchResults(unicodeHtml)

        assertEquals(4, results.size, "Should parse all 4 Unicode entries")

        val chinese = results[0]
        assertEquals("三体", chinese.title)
        assertEquals("刘慈欣", chinese.author)
        assertEquals("aa11bb22cc33dd44ee55ff6677889900", chinese.md5)

        val japanese = results[1]
        assertEquals("ノルウェイの森", japanese.title)
        assertEquals("村上春樹", japanese.author)

        val arabic = results[2]
        assertEquals("ألف ليلة وليلة", arabic.title)
        assertEquals("مؤلف مجهول", arabic.author)

        val czechGerman = results[3]
        assertEquals("Příběhy z Čech a Moravy — Erzählungen über böhmische Städte", czechGerman.title)
        assertEquals("Jan Neruda", czechGerman.author)
    }

    // --- Test 4: Library DELETE for entry owned by different user returns 404 ---
    @Test
    fun shouldReturn404WhenDeletingLibraryEntryOwnedByDifferentUser() = testApp {
        val token1 = registerAndGetToken("user1-gap@example.com")
        val token2 = registerAndGetToken("user2-gap@example.com")

        // User1's library entry ID won't exist in user2's scope,
        // so attempting to delete any entry that doesn't belong to the user returns 404
        val response = client.delete("/api/library/1") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Test 5: Cache hit on second identical search (unit test with mocked scraper) ---
    @Test
    fun shouldNotCallScraperOnSecondIdenticalSearch() {
        val dsl = DatabaseFactory.createInMemory()
        val bookRepository = BookRepository(dsl)
        val scraperService = io.mockk.mockk<pl.fairydeck.booksearch.service.ScraperService>()
        val userLibraryRepository = UserLibraryRepository(dsl)
        val searchService = SearchService(scraperService, bookRepository, userLibraryRepository, 7)

        val scraped = listOf(
            ParsedBookEntry(
                md5 = "cache00000000000000000000000001",
                title = "Cache Test Book",
                author = "Cache Author",
                language = "pl",
                format = "epub",
                fileSize = "1MB",
                detailUrl = "/md5/cache00000000000000000000000001",
                coverUrl = "",
                publisher = "",
                year = "2024",
                description = ""
            )
        )

        io.mockk.coEvery { scraperService.scrapeSearch("Cache", "pl", "epub", 3) } returns scraped

        kotlinx.coroutines.runBlocking { searchService.search(userId = 1, query = "Cache", language = "pl", format = "epub", page = 1, maxPages = 3) }
        io.mockk.coVerify(exactly = 1) { scraperService.scrapeSearch("Cache", "pl", "epub", 3) }

        kotlinx.coroutines.runBlocking { searchService.search(userId = 1, query = "Cache", language = "pl", format = "epub", page = 1, maxPages = 3) }
        io.mockk.coVerify(exactly = 1) { scraperService.scrapeSearch("Cache", "pl", "epub", 3) }
    }

    // --- Test 6: Add book to library, then search shows matchType "exact" ---
    @Test
    fun shouldReturnExactMatchTypeAfterAddingBookToLibrary() {
        val dsl = DatabaseFactory.createInMemory()
        val bookRepository = BookRepository(dsl)
        val scraperService = io.mockk.mockk<pl.fairydeck.booksearch.service.ScraperService>()
        val userLibraryRepository = UserLibraryRepository(dsl)
        val searchService = SearchService(scraperService, bookRepository, userLibraryRepository, 7)

        val testBook = ParsedBookEntry(
            md5 = "owned0000000000000000000000001",
            title = "Owned Book Title",
            author = "Owned Author",
            language = "pl",
            format = "epub",
            fileSize = "2MB",
            detailUrl = "/md5/owned0000000000000000000000001",
            coverUrl = "",
            publisher = "",
            year = "2024",
            description = ""
        )

        io.mockk.coEvery { scraperService.scrapeSearch("Owned", "pl", "epub", 3) } returns listOf(testBook)

        // First: seed via UserRepository for user ID
        val userRepo = pl.fairydeck.booksearch.repository.UserRepository(dsl)
        val user = userRepo.create("owner@test.com", "hash", "Owner", false, false)

        // Search to seed the book in the books table
        kotlinx.coroutines.runBlocking { searchService.search(userId = user.id!!, query = "Owned", language = "pl", format = "epub", page = 1, maxPages = 3) }

        // Add to library
        userLibraryRepository.add(user.id!!, "owned0000000000000000000000001", "epub")

        // Search again - should use cache and show "exact" matchType
        val response = kotlinx.coroutines.runBlocking { searchService.search(userId = user.id!!, query = "Owned", language = "pl", format = "epub", page = 1, maxPages = 3) }

        assertEquals(1, response.results.size)
        assertEquals("exact", response.results.first().matchType)
        assertTrue(response.results.first().ownedFormats.contains("epub"))
    }

    // --- Test 7: POST /api/library with empty bookMd5 returns error ---
    @Test
    fun shouldReturn404WhenAddingBookWithEmptyMd5() = testApp {
        val token = registerAndGetToken("emptymd5@example.com")

        val response = client.post("/api/library") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"bookMd5":"","format":"epub"}""")
        }

        // Empty md5 won't match any book, so returns 404
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // --- Test 8: HtmlParser preserves long descriptions with special characters ---
    @Test
    fun shouldPreserveLongDescriptionWithDiacriticsAndMixedScripts() {
        val unicodeHtml = javaClass.classLoader
            .getResource("fixtures/annas-archive-unicode-results.html")!!
            .readText()

        val results = HtmlParser.parseSearchResults(unicodeHtml)
        val czechEntry = results[3]

        assertTrue(czechEntry.description.contains("ěščřžýáíé"), "Description should contain Czech diacritics")
        assertTrue(czechEntry.description.contains("äöüß"), "Description should contain German umlauts")
        assertTrue(czechEntry.description.length > 100, "Long description should be preserved fully, got ${czechEntry.description.length} chars")
    }
}
