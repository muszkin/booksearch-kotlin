package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

class SolvearrClient(
    private val config: ScraperConfig,
    private val httpClientOverride: HttpClient? = null
) {

    private val logger = LoggerFactory.getLogger(SolvearrClient::class.java)
    private val requestMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val httpClient = httpClientOverride ?: HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
                readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    suspend fun fetchPage(url: String): String {
        return try {
            fetchPageWithCookies(url).html
        } catch (error: ScraperException) {
            val proxyUrl = config.solvearrProxyUrl
            if (proxyUrl == null || !isChallengeFailure(error)) {
                throw error
            }

            logger.warn("Direct browser verification was challenged for {}; retrying through proxy", url)
            createSession(GENERAL_PROXY_SESSION_ID, proxyUrl)
            fetchPageWithCookies(url, sessionId = GENERAL_PROXY_SESSION_ID).html
        }
    }

    suspend fun fetchPageWithCookies(
        url: String,
        maxTimeoutMs: Int = SOLVEARR_TIMEOUT_MS,
        sessionId: String? = null
    ): PageWithCookies {
        val requestBody = SolvearrRequest(
            cmd = "request.get",
            url = url,
            maxTimeout = maxTimeoutMs,
            session = sessionId,
            sessionTtlMinutes = sessionId?.let { config.solvearrSessionTtlMinutes }
        )

        return try {
            val (httpStatus, solvearrResponse) = execute(requestBody)

            if (!httpStatus.isSuccess() || solvearrResponse.status != "ok") {
                logger.error(
                    "Solvearr failed for URL {}: httpStatus={}, status={}, message={}",
                    url,
                    httpStatus,
                    solvearrResponse.status,
                    solvearrResponse.message
                )
                val reason = solvearrResponse.message.ifBlank {
                    "HTTP $httpStatus"
                }
                throw ScraperException("Browser verification failed: $reason")
            }

            val solution = solvearrResponse.solution
                ?: throw ScraperException("Empty response from Solvearr")

            val html = solution.response.ifBlank {
                throw ScraperException("Empty response from Solvearr")
            }
            if (solution.status >= 400 || ImpersonatorHttpClient.isChallengePage(html)) {
                throw ScraperException("Browser verification returned a challenge page")
            }

            val cookies = solution.cookies
                .associate { it.name to it.value }

            PageWithCookies(
                html = html,
                cookies = cookies,
                userAgent = solution.userAgent
            )

        } catch (e: ScraperException) {
            throw e
        } catch (e: Exception) {
            logger.error("Solvearr unavailable for URL: {}", url, e)
            throw ScraperException("Solvearr unavailable: ${e.message}", e)
        }
    }

    suspend fun createSession(sessionId: String, proxyUrl: String? = null) {
        val (httpStatus, response) = execute(
            SolvearrRequest(
                cmd = "sessions.create",
                session = sessionId,
                proxy = proxyUrl?.let(::SolvearrProxy)
            )
        )
        if (!httpStatus.isSuccess() || response.status != "ok") {
            val reason = response.message.ifBlank { "HTTP $httpStatus" }
            throw ScraperException("Could not create browser session: $reason")
        }
    }

    suspend fun destroySession(sessionId: String) {
        try {
            val (httpStatus, response) = execute(
                SolvearrRequest(cmd = "sessions.destroy", session = sessionId)
            )
            if (!httpStatus.isSuccess() || response.status != "ok") {
                logger.warn(
                    "Could not destroy Solvearr session {}: httpStatus={}, status={}, message={}",
                    sessionId,
                    httpStatus,
                    response.status,
                    response.message
                )
            }
        } catch (e: Exception) {
            logger.warn("Could not destroy Solvearr session {}: {}", sessionId, e.message)
        }
    }

    private suspend fun execute(requestBody: SolvearrRequest): Pair<HttpStatusCode, SolvearrResponse> {
        return requestMutex.withLock {
            val response = httpClient.post("${config.solvearrUrl}/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
            }
            val responseBody = response.bodyAsText()
            val solvearrResponse = try {
                json.decodeFromString<SolvearrResponse>(responseBody)
            } catch (e: Exception) {
                throw ScraperException(
                    "Invalid response from browser verification service (HTTP ${response.status})",
                    e
                )
            }

            response.status to solvearrResponse
        }
    }

    fun close() {
        httpClient.close()
    }

    private fun isChallengeFailure(error: Throwable): Boolean =
        generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .any {
                it.contains("challenge", ignoreCase = true) ||
                    it.contains("browser verification", ignoreCase = true)
            }

    companion object {
        private const val SOLVEARR_TIMEOUT_MS = 90000
        private const val GENERAL_PROXY_SESSION_ID = "booksearch-annas-proxy"
    }
}

data class PageWithCookies(
    val html: String,
    val cookies: Map<String, String>,
    val userAgent: String = ""
)

@Serializable
private data class SolvearrRequest(
    val cmd: String,
    val url: String? = null,
    val maxTimeout: Int? = null,
    val session: String? = null,
    val proxy: SolvearrProxy? = null,
    @SerialName("session_ttl_minutes")
    val sessionTtlMinutes: Int? = null
)

@Serializable
private data class SolvearrProxy(
    val url: String
)

@Serializable
private data class SolvearrResponse(
    val status: String = "",
    val message: String = "",
    val solution: SolvearrSolution? = null
)

@Serializable
private data class SolvearrSolution(
    val url: String = "",
    val status: Int = 0,
    val response: String = "",
    val cookies: List<SolvearrCookie> = emptyList(),
    val userAgent: String = ""
)

@Serializable
private data class SolvearrCookie(
    val name: String = "",
    val value: String = "",
    val domain: String = "",
    val path: String = "",
    val expiry: Long = 0,
    val httpOnly: Boolean = false,
    val secure: Boolean = false
)
