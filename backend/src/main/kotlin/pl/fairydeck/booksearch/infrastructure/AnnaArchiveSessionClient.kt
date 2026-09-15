package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/**
 * Fetches Anna's Archive pages over plain HTTP using a member session.
 *
 * DDoS-Guard challenges `/search` for anonymous callers but serves it directly to a
 * signed-in member, so an authenticated request avoids the headless browser entirely.
 */
class AnnaArchiveSessionClient(
    private val config: ScraperConfig,
    private val httpClientOverride: HttpClient? = null
) {

    private val logger = LoggerFactory.getLogger(AnnaArchiveSessionClient::class.java)
    private val sessionMutex = Mutex()

    @Volatile
    private var sessionToken: String? = null

    // Redirects are followed explicitly below rather than by the client: the sign-in
    // response sets the session cookie on its 302, and a client that follows the hop
    // transparently only exposes the final response's headers, losing the cookie.
    private val httpClient = httpClientOverride ?: HttpClient(OkHttp) {
        followRedirects = false
        engine {
            config {
                followRedirects(false)
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    val isConfigured: Boolean get() = config.annaArchiveApiKey != null

    /** Returns the page, or null when no member key is configured. */
    suspend fun fetchPageOrNull(url: String): String? =
        if (isConfigured) fetchPage(url) else null

    suspend fun fetchPage(url: String): String {
        val token = sessionToken ?: authenticate(originOf(url))
        val response = requestPage(url, token)

        if (isRejected(response.first, response.second)) {
            logger.info("Member session was rejected for {}, signing in again", safeHost(url))
            val refreshed = authenticate(originOf(url), force = true)
            val retried = requestPage(url, refreshed)
            if (isRejected(retried.first, retried.second)) {
                throw ScraperException("Anna's Archive rejected the member session for ${safeHost(url)}")
            }
            return retried.second
        }

        return response.second
    }

    private suspend fun requestPage(url: String, token: String): Pair<Int, String> {
        var current = url
        repeat(MAX_REDIRECTS) {
            val response = httpClient.get(current) {
                header(HttpHeaders.Cookie, "$SESSION_COOKIE_NAME=$token")
                header(HttpHeaders.UserAgent, config.userAgent)
                header(HttpHeaders.Accept, HTML_ACCEPT)
            }

            val location = response.headers[HttpHeaders.Location]
            if (!response.status.value.isRedirect() || location == null) {
                return response.status.value to response.bodyAsText()
            }
            current = resolveLocation(current, location)
        }
        throw ScraperException("Too many redirects fetching ${safeHost(url)}")
    }

    private fun resolveLocation(current: String, location: String): String =
        runCatching { java.net.URI(current).resolve(location).toString() }.getOrDefault(location)

    private fun isRejected(statusCode: Int, body: String): Boolean =
        statusCode == FORBIDDEN || ImpersonatorHttpClient.isChallengePage(body)

    private suspend fun authenticate(origin: String, force: Boolean = false): String =
        sessionMutex.withLock {
            val cached = sessionToken
            if (cached != null && !force) return@withLock cached

            val apiKey = config.annaArchiveApiKey
                ?: throw ScraperException("No Anna's Archive member key configured")

            val response = httpClient.submitForm(
                url = "$origin/account/",
                formParameters = Parameters.build { append("key", apiKey) }
            ) {
                header(HttpHeaders.UserAgent, config.userAgent)
            }

            val token = response.headers
                .getAll(HttpHeaders.SetCookie)
                .orEmpty()
                .map(::parseServerSetCookieHeader)
                .firstOrNull { it.name == SESSION_COOKIE_NAME }
                ?.value
                ?: throw ScraperException("Anna's Archive did not return a member session")

            if (!response.status.isSuccess() && !response.status.value.isRedirect()) {
                throw ScraperException("Anna's Archive rejected the member key (HTTP ${response.status.value})")
            }

            logger.info("Signed in to Anna's Archive on {}", safeHost(origin))
            sessionToken = token
            token
        }

    private fun Int.isRedirect(): Boolean = this in 300..399

    private fun originOf(url: String): String {
        val uri = java.net.URI(url)
        return "${uri.scheme}://${uri.host}"
    }

    private fun safeHost(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "remote host"

    fun close() {
        if (httpClientOverride == null) httpClient.close()
    }

    private companion object {
        const val SESSION_COOKIE_NAME = "aa_account_id2"
        const val FORBIDDEN = 403
        const val HTML_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        const val MAX_REDIRECTS = 5
    }
}
