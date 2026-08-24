package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.AnnaArchiveRecordClient
import pl.fairydeck.booksearch.infrastructure.BookRecord
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.OpenRouterClient
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.repository.BookRepository

class BookDescriptionEnrichmentTest {

    private lateinit var dsl: DSLContext
    private lateinit var bookRepository: BookRepository
    private lateinit var service: BookDescriptionService

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        bookRepository = BookRepository(dsl)
        bookRepository.upsertFromSearch(listOf(book()))
        val recordClient = mockk<AnnaArchiveRecordClient>()
        coEvery { recordClient.fetch(any(), any()) } coAnswers {
            delay(50)
            BookRecord(BLURB, "9788308068069")
        }
        val openRouterClient = mockk<OpenRouterClient>()
        every { openRouterClient.isConfigured } returns false
        val mirrorService = mockk<MirrorService>()
        every { mirrorService.getDownloadMirrors() } returns listOf("https://annas-archive.gd")
        service = BookDescriptionService(bookRepository, recordClient, openRouterClient, mirrorService)
    }

    @Test
    fun enrichmentReturnsBeforeTheLookupFinishes() {
        service.enrichInBackground(MD5)

        assertEquals(null, bookRepository.findByMd5(MD5)!!.descriptionSource)
    }

    @Test
    fun enrichmentEventuallyStoresTheDescription() = runBlocking {
        service.enrichInBackground(MD5)

        withTimeout(5_000) {
            while (bookRepository.findByMd5(MD5)!!.description.isNullOrBlank()) delay(20)
        }
        assertEquals("annas-archive", bookRepository.findByMd5(MD5)!!.descriptionSource)
    }

    private fun book() = ParsedBookEntry(
        md5 = MD5, title = "Solaris", author = "Stanisław Lem", language = "Polish [pl]",
        format = "epub", fileSize = "1.2MB", detailUrl = "/md5/$MD5", coverUrl = "",
        publisher = "WL", year = "1961", description = ""
    )

    private companion object {
        const val MD5 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
        const val BLURB = "Stacja badawcza na orbicie myślącego oceanu, który odpowiada uczonym ich własnymi wspomnieniami."
    }
}
