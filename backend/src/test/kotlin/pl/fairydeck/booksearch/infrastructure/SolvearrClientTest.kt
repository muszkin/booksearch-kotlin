package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SolvearrClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun shouldReturnHtmlAndCookiesFromSolvearrResponse() = runBlocking {
        val solvearrJsonResponse = """
        {
            "status": "ok",
            "message": "",
            "solution": {
                "url": "https://annas-archive.org/md5/a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6",
                "status": 200,
                "response": "<html><body>Detail page</body></html>",
                "cookies": [
                    {"name": "cf_clearance", "value": "abc123xyz", "domain": ".annas-archive.org", "path": "/", "expiry": 1700000000, "httpOnly": false, "secure": true},
                    {"name": "session_id", "value": "sess456", "domain": ".annas-archive.org", "path": "/", "expiry": 1700000000, "httpOnly": true, "secure": true}
                ]
            }
        }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(solvearrJsonResponse),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = SolvearrClient(
            config = ScraperConfig(
                solvearrUrl = "http://localhost:8191",
                userAgent = "test",
                requestDelayMs = 0,
                maxRetries = 0,
                backoffMultiplier = 1.0,
                cacheTtlDays = 1
            ),
            httpClientOverride = HttpClient(mockEngine)
        )

        val result = client.fetchPageWithCookies("https://annas-archive.org/md5/a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")

        assertEquals("<html><body>Detail page</body></html>", result.html)
        assertEquals(2, result.cookies.size)
        assertEquals("abc123xyz", result.cookies["cf_clearance"])
        assertEquals("sess456", result.cookies["session_id"])

        client.close()
    }
}
