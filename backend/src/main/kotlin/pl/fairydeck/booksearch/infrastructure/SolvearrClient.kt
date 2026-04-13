package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

class SolvearrClient(private val config: ScraperConfig) {

    private val logger = LoggerFactory.getLogger(SolvearrClient::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(true)
            }
        }
    }

    suspend fun fetchPage(url: String): String {
        val requestBody = SolvearrRequest(
            cmd = "request.get",
            url = url,
            maxTimeout = SOLVEARR_TIMEOUT_MS
        )

        return try {
            val response = httpClient.post("${config.solvearrUrl}/v1") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(requestBody))
            }

            if (!response.status.isSuccess()) {
                logger.error("Solvearr returned status {} for URL: {}", response.status, url)
                throw ScraperException("Solvearr request failed with status ${response.status}")
            }

            val responseBody = response.bodyAsText()
            val solvearrResponse = json.decodeFromString<SolvearrResponse>(responseBody)

            if (solvearrResponse.status != "ok") {
                logger.error("Solvearr returned non-ok status: {} for URL: {}", solvearrResponse.status, url)
                throw ScraperException("Solvearr returned status: ${solvearrResponse.status}")
            }

            solvearrResponse.solution?.response ?: throw ScraperException("Empty response from Solvearr")

        } catch (e: ScraperException) {
            throw e
        } catch (e: Exception) {
            logger.error("Solvearr unavailable for URL: {}", url, e)
            throw ScraperException("Solvearr unavailable: ${e.message}", e)
        }
    }

    fun close() {
        httpClient.close()
    }

    companion object {
        private const val SOLVEARR_TIMEOUT_MS = 60000
    }
}

@Serializable
private data class SolvearrRequest(
    val cmd: String,
    val url: String,
    val maxTimeout: Int
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
    val response: String = ""
)
