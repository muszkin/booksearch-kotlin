package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry

class BookDescriptionRepositoryTest {

    private lateinit var dsl: DSLContext
    private lateinit var bookRepository: BookRepository

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        bookRepository = BookRepository(dsl)
        bookRepository.upsertFromSearch(listOf(book()))
    }

    @Test
    fun storesADescriptionTogetherWithWhereItCameFrom() {
        bookRepository.saveDescription(MD5, "A real publisher blurb", "annas-archive", isbn = "9788375780635")

        val stored = bookRepository.findByMd5(MD5)!!
        assertEquals("A real publisher blurb", stored.description)
        assertEquals("annas-archive", stored.descriptionSource)
        assertEquals("9788375780635", stored.isbn)
        assertNotNull(stored.descriptionCheckedAt)
    }

    @Test
    fun recordsALookupThatFoundNothingSoItIsNotRepeated() {
        bookRepository.markDescriptionChecked(MD5)

        val stored = bookRepository.findByMd5(MD5)!!
        assertNotNull(stored.descriptionCheckedAt)
        assertNull(stored.descriptionSource)
    }

    @Test
    fun keepsAnExistingIsbnWhenALaterLookupHasNone() {
        bookRepository.saveDescription(MD5, "blurb", "annas-archive", isbn = "9788375780635")

        bookRepository.saveDescription(MD5, "generated", "openrouter", isbn = null)

        val stored = bookRepository.findByMd5(MD5)!!
        assertEquals("9788375780635", stored.isbn)
        assertEquals("openrouter", stored.descriptionSource)
    }

    @Test
    fun storesAnIsbnWithoutClaimingADescriptionSource() {
        bookRepository.saveIsbn(MD5, "9788375780635")

        val stored = bookRepository.findByMd5(MD5)!!
        assertEquals("9788375780635", stored.isbn)
        assertNull(stored.descriptionSource)
    }

    @Test
    fun searchUpsertsDoNotWipeAnAlreadyResolvedDescription() {
        bookRepository.saveDescription(MD5, "A real publisher blurb", "annas-archive", isbn = null)

        bookRepository.upsertFromSearch(listOf(book()))

        val stored = bookRepository.findByMd5(MD5)!!
        assertEquals("A real publisher blurb", stored.description)
        assertEquals("annas-archive", stored.descriptionSource)
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
    }
}
