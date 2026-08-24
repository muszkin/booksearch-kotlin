package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.AnnaArchiveRecordClient
import pl.fairydeck.booksearch.infrastructure.BookRecord
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.OpenRouterClient
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.repository.BookRepository

class BookDescriptionServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var bookRepository: BookRepository
    private lateinit var recordClient: AnnaArchiveRecordClient
    private lateinit var openRouterClient: OpenRouterClient
    private lateinit var mirrorService: MirrorService
    private lateinit var service: BookDescriptionService

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        bookRepository = BookRepository(dsl)
        bookRepository.upsertFromSearch(listOf(book()))
        recordClient = mockk()
        openRouterClient = mockk()
        mirrorService = mockk()
        every { mirrorService.getDownloadMirrors() } returns listOf(MIRROR)
        every { openRouterClient.isConfigured } returns true
        service = BookDescriptionService(bookRepository, recordClient, openRouterClient, mirrorService)
    }

    @Test
    fun prefersThePublisherBlurbOverAGeneratedOne() = runBlocking {
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord(BLURB, "9788308068069")

        val result = service.describe(MD5)

        assertEquals(BLURB, result?.description)
        assertEquals("annas-archive", result?.source)
        assertEquals("9788308068069", result?.isbn)
        coVerify(exactly = 0) { openRouterClient.describeBook(any(), any()) }
    }

    @Test
    fun asksTheModelOnlyWhenTheArchiveHasNoBlurb() = runBlocking {
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord(null, "9788308068069")
        coEvery { openRouterClient.describeBook("Solaris", "Stanisław Lem") } returns GENERATED

        val result = service.describe(MD5)

        assertEquals(GENERATED, result?.description)
        assertEquals("openrouter", result?.source)
        assertEquals("9788308068069", result?.isbn)
    }

    @Test
    fun treatsAStubBlurbAsMissing() = runBlocking {
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord("Fantasy,Sci-Fi", null)
        coEvery { openRouterClient.describeBook(any(), any()) } returns GENERATED

        assertEquals(GENERATED, service.describe(MD5)?.description)
    }

    @Test
    fun returnsNothingAndStaysSilentWhenNeitherSourceHasAnything() = runBlocking {
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord(null, null)
        coEvery { openRouterClient.describeBook(any(), any()) } returns null

        assertNull(service.describe(MD5))
    }

    @Test
    fun servesARepeatRequestFromStorageWithoutCallingOut() = runBlocking {
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord(BLURB, null)
        service.describe(MD5)

        val second = service.describe(MD5)

        assertEquals(BLURB, second?.description)
        coVerify(exactly = 1) { recordClient.fetch(any(), any()) }
    }

    @Test
    fun doesNotRetryALookupThatAlreadyCameBackEmpty() = runBlocking {
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord(null, null)
        coEvery { openRouterClient.describeBook(any(), any()) } returns null
        service.describe(MD5)

        service.describe(MD5)

        coVerify(exactly = 1) { recordClient.fetch(any(), any()) }
    }

    @Test
    fun skipsTheModelEntirelyWhenNoApiKeyIsConfigured() = runBlocking {
        every { openRouterClient.isConfigured } returns false
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord(null, null)

        assertNull(service.describe(MD5))
        coVerify(exactly = 0) { openRouterClient.describeBook(any(), any()) }
    }

    @Test
    fun returnsNothingForABookItHasNeverSeen() = runBlocking {
        assertNull(service.describe("ffffffffffffffffffffffffffffffff"))
    }

    @Test
    fun regeneratingReplacesAWrongBlurbWithAGeneratedOne() = runBlocking {
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord(BLURB, null)
        service.describe(MD5)
        coEvery { openRouterClient.describeBook("Solaris", "Stanisław Lem") } returns GENERATED

        val result = service.regenerate(MD5)

        assertEquals(GENERATED, result?.description)
        assertEquals("openrouter", result?.source)
        assertEquals(GENERATED, bookRepository.findByMd5(MD5)!!.description)
    }

    @Test
    fun regeneratingDoesNotConsultTheArchiveAgain() = runBlocking {
        coEvery { openRouterClient.describeBook(any(), any()) } returns GENERATED

        service.regenerate(MD5)

        coVerify(exactly = 0) { recordClient.fetch(any(), any()) }
    }

    @Test
    fun regeneratingKeepsTheStoredBlurbWhenTheModelDeclines() = runBlocking {
        coEvery { recordClient.fetch(MD5, MIRROR) } returns BookRecord(BLURB, null)
        service.describe(MD5)
        coEvery { openRouterClient.describeBook(any(), any()) } returns null

        assertNull(service.regenerate(MD5))
        assertEquals(BLURB, bookRepository.findByMd5(MD5)!!.description)
    }

    @Test
    fun regeneratingIsUnavailableWithoutAnApiKey() = runBlocking {
        every { openRouterClient.isConfigured } returns false

        assertNull(service.regenerate(MD5))
        coVerify(exactly = 0) { openRouterClient.describeBook(any(), any()) }
    }

    @Test
    fun regeneratingReturnsNothingForABookItHasNeverSeen() = runBlocking {
        assertNull(service.regenerate("ffffffffffffffffffffffffffffffff"))
    }

    private fun book() = ParsedBookEntry(
        md5 = MD5,
        title = "Solaris",
        author = "Stanisław Lem",
        language = "Polish [pl]",
        format = "epub",
        fileSize = "1.2MB",
        detailUrl = "/md5/$MD5",
        coverUrl = "",
        publisher = "WL",
        year = "1961",
        description = ""
    )

    private companion object {
        const val MD5 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
        const val MIRROR = "https://annas-archive.gd"
        const val BLURB = "Stacja badawcza na orbicie myślącego oceanu, który odpowiada uczonym ich własnymi wspomnieniami."
        const val GENERATED = "A philosophical novel about a research station orbiting a sentient ocean and the limits of contact."
    }
}