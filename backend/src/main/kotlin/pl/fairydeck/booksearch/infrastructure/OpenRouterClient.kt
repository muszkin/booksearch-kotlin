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
import kotlinx.coroutines.CancellationException
import io.ktor.client.plugins.HttpTimeout

class OpenRouterClient(
    private val config: OpenRouterConfig,
    private val httpClientOverride: HttpClient? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = httpClientOverride ?: HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 120_000 } }

    suspend fun listFreeTextModels(): List<OpenRouterModel> {
        return fetchModels()
            .filter { it.isFreeTextModel() }
            .map { OpenRouterModel(it.id, it.name) }
    }

    suspend fun translate(modelId: String, prompt: String): OpenRouterCompletion {
        if (fetchModels().none { it.id == modelId && it.isFreeTextModel() }) {
            throw OpenRouterException("Selected OpenRouter model is not currently free for text requests", code = "model_no_longer_free")
        }

        val response = request {
            post("${config.baseUrl.trimEnd('/')}/api/v1/chat/completions") {
                withAuth()
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(OpenRouterCompletionRequest(
                    model = modelId,
                    messages = listOf(OpenRouterRequestMessage(role = "user", content = prompt)),
                    provider = OpenRouterProvider(allowFallbacks = false)
                )))
            }
        }
        if (!response.status.isSuccess()) {
            throw responseError(response.status.value)
        }

        val completion = decode<OpenRouterCompletionResponse>(response.bodyAsText())
        val content = completion.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
            ?: throw OpenRouterException("OpenRouter completion response did not contain text")
        return OpenRouterCompletion(content, completion.usage?.promptTokens ?: 0, completion.usage?.completionTokens ?: 0)
    }

    fun close() {
        httpClient.close()
    }

    private suspend fun request(block: suspend HttpClient.() -> io.ktor.client.statement.HttpResponse): io.ktor.client.statement.HttpResponse =
        try {
            httpClient.block()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            throw OpenRouterException("OpenRouter request failed", retryable = true)
        }

    private inline fun <reified T> decode(body: String): T =
        try {
            json.decodeFromString(body)
        } catch (_: Exception) {
            throw OpenRouterException("OpenRouter returned an invalid response")
        }

    private suspend fun fetchModels(): List<OpenRouterModelResponse> {
        val response = request {
            get("${config.baseUrl.trimEnd('/')}/api/v1/models") {
                withAuth()
            }
        }
        if (!response.status.isSuccess()) {
            throw responseError(response.status.value)
        }
        return decode<OpenRouterModelsResponse>(response.bodyAsText()).data
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
    ) {
        fun isFreeTextModel(): Boolean =
            architecture.outputModalities.contains("text") && pricing.isFreeTextRequest()
    }

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
        val messages: List<OpenRouterRequestMessage>,
        val provider: OpenRouterProvider
    )

    @Serializable
    private data class OpenRouterProvider(
        @SerialName("allow_fallbacks") val allowFallbacks: Boolean
    )

    @Serializable
    private data class OpenRouterRequestMessage(val role: String, val content: String)

    @Serializable
    private data class OpenRouterMessage(val role: String = "user", val content: String)

    @Serializable
    private data class OpenRouterCompletionResponse(val choices: List<OpenRouterChoice> = emptyList(), val usage: OpenRouterUsage? = null)

    @Serializable
    private data class OpenRouterUsage(@SerialName("prompt_tokens") val promptTokens: Int = 0, @SerialName("completion_tokens") val completionTokens: Int = 0)

    private fun responseError(status: Int) = OpenRouterException("OpenRouter request failed with status $status", retryable = status == 429 || status in 500..599)

    @Serializable
    private data class OpenRouterChoice(val message: OpenRouterMessage? = null)
}

data class OpenRouterModel(val id: String, val name: String)

data class OpenRouterCompletion(val content: String, val inputTokens: Int = 0, val outputTokens: Int = 0)

class OpenRouterException(message: String, val retryable: Boolean = false, val code: String = "translation_request_failed") : RuntimeException(message)
