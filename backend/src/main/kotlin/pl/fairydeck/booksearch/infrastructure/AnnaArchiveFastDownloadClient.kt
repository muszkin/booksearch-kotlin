package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI

class AnnaArchiveFastDownloadClient(
    private val config: ScraperConfig,
    private val httpClientOverride: HttpClient? = null
) {

    private val logger = LoggerFactory.getLogger(AnnaArchiveFastDownloadClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = httpClientOverride ?: HttpClient(OkHttp)

    suspend fun resolveDownload(bookMd5: String, mirrors: List<String>): FastDownload? {
        val apiKey = config.annaArchiveApiKey ?: return null

        for (mirror in mirrors) {
            try {
                val response = httpClient.get("$mirror/dyn/api/fast_download.json") {
                    parameter("md5", bookMd5)
                    parameter("key", apiKey)
                }
                val body = json.decodeFromString<FastDownloadResponse>(response.bodyAsText())

                if (response.status.isSuccess()) {
                    val downloadUrl = body.downloadUrl?.takeIf(::isHttpUrl)
                    if (downloadUrl != null) {
                        logger.info(
                            "Resolved {} through Anna's Archive JSON API on {} ({} downloads left)",
                            bookMd5,
                            mirror,
                            body.accountFastDownloadInfo?.downloadsLeft ?: "unknown"
                        )
                        return FastDownload(
                            url = downloadUrl,
                            downloadsLeft = body.accountFastDownloadInfo?.downloadsLeft,
                            downloadsPerDay = body.accountFastDownloadInfo?.downloadsPerDay
                        )
                    }
                }

                logger.warn(
                    "Anna's Archive fast-download API did not resolve {} on {}: status={}, error={}",
                    bookMd5,
                    mirror,
                    response.status.value,
                    body.error.orEmpty()
                )

                if (response.status.value in NON_RETRYABLE_STATUS_CODES) {
                    return null
                }
            } catch (e: Exception) {
                logger.warn(
                    "Anna's Archive fast-download API request failed for {} on {}: {}",
                    bookMd5,
                    mirror,
                    e.javaClass.simpleName
                )
            }
        }
        return null
    }

    private fun isHttpUrl(value: String): Boolean {
        val scheme = runCatching { URI(value).scheme }.getOrNull()
        return scheme.equals("https", ignoreCase = true) ||
            scheme.equals("http", ignoreCase = true)
    }

    fun close() {
        if (httpClientOverride == null) {
            httpClient.close()
        }
    }

    companion object {
        private val NON_RETRYABLE_STATUS_CODES = setOf(401, 403, 429)
    }
}

data class FastDownload(
    val url: String,
    val downloadsLeft: Int?,
    val downloadsPerDay: Int?
)

@Serializable
private data class FastDownloadResponse(
    @SerialName("download_url")
    val downloadUrl: String? = null,
    val error: String? = null,
    @SerialName("account_fast_download_info")
    val accountFastDownloadInfo: AccountFastDownloadInfo? = null
)

@Serializable
private data class AccountFastDownloadInfo(
    @SerialName("downloads_left")
    val downloadsLeft: Int? = null,
    @SerialName("downloads_per_day")
    val downloadsPerDay: Int? = null
)
