package pl.fairydeck.booksearch.infrastructure

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AnnaArchiveRecordClientTest {

    private val sessionClient = mockk<AnnaArchiveSessionClient>()

    @Test
    fun readsTheDescriptionAndIsbnFromTheRecord() = runBlocking {
        givenRecord(
            """
            {"file_unified_data":{
               "stripped_description_best":"Ocean planet that reads minds.",
               "identifiers_unified":{"isbn13":["9788308068069"],"isbn10":["8308068065"]}
            }}
            """.trimIndent()
        )

        val record = client().fetch(MD5, "https://annas-archive.gd")

        assertEquals("Ocean planet that reads minds.", record?.description)
        assertEquals("9788308068069", record?.isbn)
    }

    @Test
    fun prefersIsbn13OverIsbn10() = runBlocking {
        givenRecord(
            """{"file_unified_data":{"identifiers_unified":{"isbn10":["8308068065"],"isbn13":["9788308068069"]}}}"""
        )

        assertEquals("9788308068069", client().fetch(MD5, MIRROR)?.isbn)
    }

    @Test
    fun fallsBackToIsbn10WhenThereIsNoIsbn13() = runBlocking {
        givenRecord("""{"file_unified_data":{"identifiers_unified":{"isbn10":["8308068065"]}}}""")

        assertEquals("8308068065", client().fetch(MD5, MIRROR)?.isbn)
    }

    @Test
    fun reportsNoDescriptionWhenTheRecordHasOnlyAnEmptyOne() = runBlocking {
        givenRecord("""{"file_unified_data":{"stripped_description_best":"   "}}""")

        assertNull(client().fetch(MD5, MIRROR)?.description)
    }

    @Test
    fun returnsNothingWhenTheRecordCannotBeRead() = runBlocking {
        coEvery { sessionClient.fetchPage(any()) } throws ScraperException("403")

        assertNull(client().fetch(MD5, MIRROR))
    }

    @Test
    fun requestsTheRecordEndpointForTheGivenHash() = runBlocking {
        val urls = mutableListOf<String>()
        coEvery { sessionClient.fetchPage(capture(urls)) } returns """{"file_unified_data":{}}"""

        client().fetch(MD5, MIRROR)

        assertEquals("$MIRROR/db/aarecord_elasticsearch/md5:$MD5.json", urls.single())
    }

    private fun givenRecord(json: String) {
        coEvery { sessionClient.fetchPage(any()) } returns json
    }

    private fun client() = AnnaArchiveRecordClient(sessionClient)

    private companion object {
        const val MD5 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
        const val MIRROR = "https://annas-archive.gd"
    }
}
