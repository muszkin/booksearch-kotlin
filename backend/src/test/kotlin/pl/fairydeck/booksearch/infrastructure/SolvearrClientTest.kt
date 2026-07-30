package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
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
                "userAgent": "FlareSolverrAgent/1.0",
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
            ),
            httpClientOverride = HttpClient(mockEngine)
        )

        val result = client.fetchPageWithCookies("https://annas-archive.org/md5/a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6")

        assertEquals("<html><body>Detail page</body></html>", result.html)
        assertEquals(2, result.cookies.size)
        assertEquals("abc123xyz", result.cookies["cf_clearance"])
        assertEquals("sess456", result.cookies["session_id"])
        assertEquals("FlareSolverrAgent/1.0", result.userAgent)

        client.close()
    }

    @Test
    fun shouldRejectChallengePageReturnedAsSuccessfulSolution() {
        val solvearrJsonResponse = """
        {
            "status": "ok",
            "message": "Challenge not detected!",
            "solution": {
                "url": "https://annas-archive.gl/slow_download/test",
                "status": 200,
                "response": "<html><head><title>DDoS-Guard</title></head><body>Checking your browser</body></html>",
                "userAgent": "FlareSolverrAgent/1.0",
                "cookies": []
            }
        }
        """.trimIndent()
        val mockEngine = MockEngine {
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
            ),
            httpClientOverride = HttpClient(mockEngine)
        )

        val error = assertThrows(ScraperException::class.java) {
            runBlocking {
                client.fetchPageWithCookies("https://annas-archive.gl/slow_download/test")
            }
        }

        assertTrue(error.message!!.contains("challenge page"))
        client.close()
    }

    @Test
    fun shouldRejectHttpErrorReportedInsideSolution() {
        val solvearrJsonResponse = """
        {
            "status": "ok",
            "message": "",
            "solution": {
                "url": "https://annas-archive.gl/slow_download/test",
                "status": 403,
                "response": "<html><body>Forbidden</body></html>",
                "userAgent": "FlareSolverrAgent/1.0",
                "cookies": []
            }
        }
        """.trimIndent()
        val mockEngine = MockEngine {
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
            ),
            httpClientOverride = HttpClient(mockEngine)
        )

        assertThrows(ScraperException::class.java) {
            runBlocking {
                client.fetchPageWithCookies("https://annas-archive.gl/slow_download/test")
            }
        }
        client.close()
    }

    @Test
    fun shouldRequestDailyRotationForPersistentSession() = runBlocking {
        val mockEngine = MockEngine { request ->
            val requestBody = (request.body as TextContent).text
            assertTrue(requestBody.contains("\"session\":\"booksearch-annas-downloads\""))
            assertTrue(requestBody.contains("\"session_ttl_minutes\":1440"))
            respond(
                content = ByteReadChannel(
                    """
                    {
                        "status": "ok",
                        "message": "",
                        "solution": {
                            "status": 200,
                            "response": "<html><body>Detail page</body></html>",
                            "cookies": []
                        }
                    }
                    """.trimIndent()
                ),
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
            ),
            httpClientOverride = HttpClient(mockEngine)
        )

        client.fetchPageWithCookies(
            "https://annas-archive.gl/md5/test",
            sessionId = "booksearch-annas-downloads"
        )

        client.close()
    }

    @Test
    fun shouldRetryChallengeThroughConfiguredProxySession() = runBlocking {
        val requestBodies = mutableListOf<String>()
        val mockEngine = MockEngine { request ->
            val requestBody = (request.body as TextContent).text
            requestBodies.add(requestBody)
            val responseBody = when {
                requestBody.contains("\"cmd\":\"sessions.create\"") ->
                    """{"status":"ok","message":"Session created successfully."}"""
                requestBody.contains("\"session\":\"booksearch-annas-proxy\"") ->
                    """
                    {
                        "status": "ok",
                        "message": "Challenge not detected!",
                        "solution": {
                            "status": 200,
                            "response": "<html><body>Anna's Archive search results</body></html>",
                            "cookies": []
                        }
                    }
                    """.trimIndent()
                else ->
                    """
                    {
                        "status": "ok",
                        "message": "Challenge not detected!",
                        "solution": {
                            "status": 200,
                            "response": "<html><title>DDoS-Guard</title><body>Checking your browser</body></html>",
                            "cookies": []
                        }
                    }
                    """.trimIndent()
            }
            respond(
                content = ByteReadChannel(responseBody),
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
                solvearrProxyUrl = "socks5://tor:9050"
            ),
            httpClientOverride = HttpClient(mockEngine)
        )

        val html = client.fetchPage("https://annas-archive.gl/search?q=test")

        assertTrue(html.contains("Anna's Archive search results"))
        assertEquals(3, requestBodies.size)
        assertTrue(requestBodies[1].contains("\"proxy\":{\"url\":\"socks5://tor:9050\"}"))
        assertTrue(requestBodies[2].contains("\"session\":\"booksearch-annas-proxy\""))
        assertTrue(requestBodies[2].contains("\"session_ttl_minutes\":1440"))
        client.close()
    }
}
