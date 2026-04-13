package pl.fairydeck.booksearch.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

class ImpersonatorHttpClient(private val config: ScraperConfig) {

    private val logger = LoggerFactory.getLogger(ImpersonatorHttpClient::class.java)

    private val httpClient: OkHttpClient = try {
        val api = com.github.zhkl0228.impersonator.ImpersonatorFactory.macChrome()
        okhttp3.OkHttpClientFactory.create(api).newHttpClient().also {
            logger.info("Using impersonator-okhttp with Chrome TLS fingerprint")
        }
    } catch (e: Exception) {
        logger.warn("Impersonator TLS init failed ({}), falling back to standard OkHttp", e.message)
        OkHttpClient.Builder().build()
    }

    private val lastRequestTime = AtomicLong(0L)

    suspend fun fetch(url: String): HttpResult {
        enforceRateLimit()

        var lastException: Exception? = null
        var delayMs = config.requestDelayMs

        repeat(config.maxRetries + 1) { attempt ->
            try {
                val result = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", config.userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.5")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val body = response.body?.string() ?: ""
                    val statusCode = response.code
                    HttpResult(body = body, statusCode = statusCode)
                }

                lastRequestTime.set(System.currentTimeMillis())

                if (isChallengePage(result.body)) {
                    logger.warn("DDoS challenge detected on attempt {} for URL: {}", attempt + 1, url)
                    if (attempt < config.maxRetries) {
                        delay(delayMs)
                        delayMs = (delayMs * config.backoffMultiplier).toLong()
                        return@repeat
                    }
                    return HttpResult(body = result.body, statusCode = 503)
                }

                return result

            } catch (e: IOException) {
                logger.error("Request failed on attempt {} for URL: {}", attempt + 1, url, e)
                lastException = e
                if (attempt < config.maxRetries) {
                    delay(delayMs)
                    delayMs = (delayMs * config.backoffMultiplier).toLong()
                }
            }
        }

        throw ScraperException(
            "Failed to fetch $url after ${config.maxRetries + 1} attempts",
            lastException
        )
    }

    private suspend fun enforceRateLimit() {
        val elapsed = System.currentTimeMillis() - lastRequestTime.get()
        val remaining = config.requestDelayMs - elapsed
        if (remaining > 0) {
            delay(remaining)
        }
    }

    companion object {
        private val CHALLENGE_MARKERS = listOf(
            "DDoS protection by",
            "DDoS-Guard",
            "Please Wait... | Cloudflare",
            "Checking your browser",
            "cf-browser-verification",
            "jschl-answer",
            "__ddg_challenge"
        )

        fun isChallengePage(html: String): Boolean {
            return CHALLENGE_MARKERS.any { marker -> html.contains(marker, ignoreCase = true) }
        }
    }
}

class ScraperException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
