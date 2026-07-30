package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AnnaArchiveFastDownloadClientTest {

    @Test
    fun shouldResolveDownloadUrlWithoutExposingApiKeyToCaller() = runBlocking {
        val engine = MockEngine { request ->
            assertEquals("secret-member-key", request.url.parameters["key"])
            respond(
                content = """
                    {
                      "download_url":"https://download.example/book.epub",
                      "account_fast_download_info":{
                        "downloads_left":49,
                        "downloads_per_day":50
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = AnnaArchiveFastDownloadClient(
            config = config(apiKey = "secret-member-key"),
            httpClientOverride = HttpClient(engine)
        )

        val result = client.resolveDownload(
            "3df78aab7902016843715ce13968603e",
            listOf("https://annas-archive.gl")
        )

        assertEquals("https://download.example/book.epub", result?.url)
        assertEquals(49, result?.downloadsLeft)
        assertEquals(50, result?.downloadsPerDay)
    }

    @Test
    fun shouldSkipApiWhenMembershipKeyIsNotConfigured() = runBlocking {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respond("""{"error":"unexpected"}""")
        }
        val client = AnnaArchiveFastDownloadClient(
            config = config(apiKey = null),
            httpClientOverride = HttpClient(engine)
        )

        val result = client.resolveDownload(
            "3df78aab7902016843715ce13968603e",
            listOf("https://annas-archive.gl")
        )

        assertNull(result)
        assertEquals(0, requestCount)
    }

    @Test
    fun shouldFallBackWhenMembershipIsInvalid() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"download_url":null,"error":"Invalid secret key"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = AnnaArchiveFastDownloadClient(
            config = config(apiKey = "invalid"),
            httpClientOverride = HttpClient(engine)
        )

        val result = client.resolveDownload(
            "3df78aab7902016843715ce13968603e",
            listOf("https://annas-archive.gl", "https://annas-archive.gd")
        )

        assertNull(result)
        assertEquals(1, engine.requestHistory.size)
    }

    private fun config(apiKey: String?) = ScraperConfig(
        solvearrUrl = "http://localhost:8191",
        userAgent = "test",
        requestDelayMs = 0,
        maxRetries = 0,
        backoffMultiplier = 1.0,
        annaArchiveApiKey = apiKey
    )
}
