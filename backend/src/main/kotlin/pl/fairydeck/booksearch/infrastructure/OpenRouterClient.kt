package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal

class OpenRouterClient(
    private val config: OpenRouterConfig,
    private val httpClientOverride: HttpClient? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = httpClientOverride ?: HttpClient(OkHttp)

    suspend fun listFreeTextModels(): List<OpenRouterModel> {
        val response = request {
            get("${config.baseUrl.trimEnd('/')}/api/v1/models") {
                withAuth()
            }
        }
        if (!response.status.isSuccess()) {
            throw OpenRouterException("OpenRouter model listing failed with status ${response.status.value}")
        }

        val models = decode<OpenRouterModelsResponse>(response.bodyAsText())
        return models.data
            .filter { it.architecture.outputModalities.contains("text") && it.pricing.isFreeTextRequest() }
            .map { OpenRouterModel(it.id, it.name) }
    }

    suspend fun translate(modelId: String, prompt: String): OpenRouterCompletion {
        val response = request {
            post("${config.baseUrl.trimEnd('/')}/api/v1/chat/completions") {
                withAuth()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(OpenRouterCompletionRequest(
                    model = modelId,
                    messages = listOf(OpenRouterMessage(content = prompt)),
                    provider = OpenRouterProvider(allowFallbacks = false)
                )))
            }
        }
        if (!response.status.isSuccess()) {
            throw OpenRouterException("OpenRouter completion failed with status ${response.status.value}")
        }

        val completion = decode<OpenRouterCompletionResponse>(response.bodyAsText())
        val content = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
            ?: throw OpenRouterException("OpenRouter completion response did not contain text")
        return OpenRouterCompletion(content)
    }

    fun close() {
        httpClient.close()
    }

    private suspend fun request(block: suspend HttpClient.() -> io.ktor.client.statement.HttpResponse): io.ktor.client.statement.HttpResponse =
        try {
            httpClient.block()
        } catch (_: Exception) {
            throw OpenRouterException("OpenRouter request failed")
        }

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString(body)
        } catch (_: Exception) {
            throw OpenRouterException("OpenRouter returned an invalid response")
        }

    private fun io.ktor.client.request.HttpRequestBuilder.withAuth() {
        header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
        accept(ContentType.Application.Json)
    }

    @Serializable
    private data class OpenRouterModelsResponse(val data: List<OpenRouterModelResponse> = emptyList())

    @Serializable
    private data class OpenRouterModelResponse(
        val id: String,
        val name: String = "",
        val architecture: OpenRouterArchitecture = OpenRouterArchitecture(),
        val pricing: OpenRouterPricing = OpenRouterPricing()
    )

    @Serializable
    private data class OpenRouterArchitecture(
        @SerialName("output_modalities") val outputModalities: List<String> = emptyList()
    )

    @Serializable
    private data class OpenRouterPricing(
        val prompt: String? = null,
        val completion: String? = null,
        val request: String? = null
    ) {
        fun isFreeTextRequest(): Boolean = listOf(prompt, completion, request).all { price ->
            price?.let { runCatching { BigDecimal(it).compareTo(BigDecimal.ZERO) == 0 }.getOrDefault(false) } == true
        }
    }

    @Serializable
    private data class OpenRouterCompletionRequest(
        val model: String,
        val messages: List<OpenRouterMessage>,
        val provider: OpenRouterProvider
    )

    @Serializable
    private data class OpenRouterProvider(
        @SerialName("allow_fallbacks") val allowFallbacks: Boolean
    )

    @Serializable
    private data class OpenRouterMessage(val role: String = "user", val content: String)

    @Serializable
    private data class OpenRouterCompletionResponse(val choices: List<OpenRouterChoice> = emptyList())

    @Serializable
    private data class OpenRouterChoice(val message: OpenRouterMessage? = null)
}

data class OpenRouterModel(val id: String, val name: String)

data class OpenRouterCompletion(val content: String)

class OpenRouterException(message: String) : RuntimeException(message)
