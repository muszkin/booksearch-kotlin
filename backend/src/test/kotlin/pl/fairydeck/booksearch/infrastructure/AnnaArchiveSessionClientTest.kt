package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnaArchiveSessionClientTest {

    @Test
    fun sendsTheMemberSessionCookieWhenFetchingAPage() = runBlocking {
        val cookiesSent = mutableListOf<String>()
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                respondWithSession()
            } else {
                cookiesSent += request.headers[HttpHeaders.Cookie].orEmpty()
                respond(SEARCH_HTML, HttpStatusCode.OK)
            }
        }

        val html = client(engine).fetchPage("https://annas-archive.gd/search?q=lem")

        assertEquals(SEARCH_HTML, html)
        assertTrue(
            cookiesSent.single().contains("aa_account_id2=$SESSION_TOKEN"),
            "page request must carry the member session cookie, got: ${cookiesSent.single()}"
        )
    }

    @Test
    fun authenticatesOnceAndReusesTheSession() = runBlocking {
        var logins = 0
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                logins++
                respondWithSession()
            } else {
                respond(SEARCH_HTML, HttpStatusCode.OK)
            }
        }
        val sessionClient = client(engine)

        sessionClient.fetchPage("https://annas-archive.gd/search?q=lem&page=1")
        sessionClient.fetchPage("https://annas-archive.gd/search?q=lem&page=2")

        assertEquals(1, logins)
    }

    @Test
    fun reauthenticatesWhenTheSessionIsRejected() = runBlocking {
        var logins = 0
        var pageRequests = 0
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Post) {
                logins++
                respondWithSession()
            } else {
                pageRequests++
                if (pageRequests == 1) {
                    respond(DDOS_GUARD_HTML, HttpStatusCode.Forbidden)
                } else {
                    respond(SEARCH_HTML, HttpStatusCode.OK)
                }
            }
        }

        val html = client(engine).fetchPage("https://annas-archive.gd/search?q=lem")

        assertEquals(SEARCH_HTML, html)
        assertEquals(2, logins)
    }

    @Test
    fun capturesTheSessionCookieEvenThoughSignInRedirects() = runBlocking {
        // The real sign-in answers 302 and sets the cookie on that hop; a client that
        // transparently follows the redirect never sees the Set-Cookie header.
        var redirectsFollowed = 0
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Post -> respondWithSession()
                request.url.encodedPath == "/account/" -> {
                    redirectsFollowed++
                    respond("", HttpStatusCode.OK)
                }
                else -> respond(SEARCH_HTML, HttpStatusCode.OK)
            }
        }

        val html = client(engine).fetchPage("https://annas-archive.gd/search?q=lem")

        assertEquals(SEARCH_HTML, html)
        assertEquals(0, redirectsFollowed, "sign-in redirect must not be followed, or the cookie is lost")
    }

    @Test
    fun followsRedirectsWhenFetchingAPage() = runBlocking {
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Post -> respondWithSession()
                request.url.encodedPath == "/search" -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://annas-archive.gd/search-moved")
                )
                else -> respond(SEARCH_HTML, HttpStatusCode.OK)
            }
        }

        assertEquals(SEARCH_HTML, client(engine).fetchPage("https://annas-archive.gd/search?q=lem"))
    }

    @Test
    fun isUnavailableWithoutAMemberKey() = runBlocking {
        val engine = MockEngine { respond(SEARCH_HTML, HttpStatusCode.OK) }
        val sessionClient = AnnaArchiveSessionClient(
            config = config(apiKey = null),
            httpClientOverride = HttpClient(engine)
        )

        assertNull(sessionClient.fetchPageOrNull("https://annas-archive.gd/search?q=lem"))
    }

    private fun client(engine: MockEngine) = AnnaArchiveSessionClient(
        config = config(apiKey = "secret-member-key"),
        httpClientOverride = HttpClient(engine)
    )

    private fun MockRequestHandleScope.respondWithSession() = respond(
        content = "",
        status = HttpStatusCode.Found,
        headers = headersOf(HttpHeaders.SetCookie, "aa_account_id2=$SESSION_TOKEN; Path=/; HttpOnly; Secure")
    )

    private fun config(apiKey: String?) = ScraperConfig(
        solvearrUrl = "http://localhost:8191",
        userAgent = "test-agent",
        requestDelayMs = 0,
        maxRetries = 1,
        backoffMultiplier = 2.0,
        annaArchiveApiKey = apiKey
    )

    private companion object {
        const val SESSION_TOKEN = "session-token-value"
        const val SEARCH_HTML = """<div class="js-aarecord-list-outer">ok</div>"""
        const val DDOS_GUARD_HTML = """<html><head><title>DDoS-Guard</title></head></html>"""
    }
}
