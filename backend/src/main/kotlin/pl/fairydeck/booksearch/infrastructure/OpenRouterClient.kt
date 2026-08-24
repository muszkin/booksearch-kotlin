package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Asks OpenRouter for a short description of a book when Anna's Archive has none.
 *
 * The archive holds a great many obscure and self-published titles, and a model asked
 * about one will produce confident invention. The prompt therefore demands a fixed
 * marker instead of a guess, and answers that hedge are discarded rather than shown.
 */
class OpenRouterClient(
    private val config: OpenRouterConfig,
    private val httpClientOverride: HttpClient? = null,
    private val settings: () -> DescriptionPromptSettings = { DescriptionPromptSettings.DEFAULT }
) {

    private val logger = LoggerFactory.getLogger(OpenRouterClient::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = httpClientOverride ?: HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            }
        }
    }

    val isConfigured: Boolean get() = config.apiKey != null

    suspend fun describeBook(title: String, author: String): String? {
        val apiKey = config.apiKey ?: return null
        val prompt = settings()

        val request = CompletionRequest(
            model = config.model,
            messages = listOf(
                Message("system", prompt.style.trim() + "\n" + GUARD),
                Message("user", "Title: $title\nAuthor: $author")
            )
        )

        val response = try {
            httpClient.post(COMPLETIONS_URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(CompletionRequest.serializer(), request))
            }
        } catch (e: Exception) {
            logger.info("OpenRouter unreachable for '{}': {}", title, e.message)
            return null
        }

        if (!response.status.isSuccess()) {
            logger.info("OpenRouter refused a description for '{}': HTTP {}", title, response.status.value)
            return null
        }

        val completion = try {
            json.decodeFromString<CompletionResponse>(response.bodyAsText())
        } catch (e: Exception) {
            logger.info("Unreadable OpenRouter response for '{}': {}", title, e.message)
            return null
        }

        val answer = completion.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (!isUsable(answer, prompt.minLength)) {
            logger.info("OpenRouter did not recognise '{}' by {}", title, author)
            return null
        }

        logger.info("Described '{}' using {}", title, completion.model ?: config.model)
        return answer
    }

    private fun isUsable(answer: String, minLength: Int): Boolean {
        if (answer.length < minLength) return false
        if (answer.equals(UNKNOWN_MARKER, ignoreCase = true)) return false
        return HEDGING_MARKERS.none { answer.contains(it, ignoreCase = true) }
    }

    fun close() {
        if (httpClientOverride == null) httpClient.close()
    }

    companion object {
        const val COMPLETIONS_URL = "https://openrouter.ai/api/v1/chat/completions"
        const val UNKNOWN_MARKER = "UNKNOWN"

        val HEDGING_MARKERS = listOf(
            "i don't have",
            "i do not have",
            "i'm not familiar",
            "i am not familiar",
            "no information",
            "unable to find",
            "cannot find",
            "as an ai"
        )

        /**
         * Appended to whatever style an administrator configures, and deliberately not
         * editable. The archive is full of obscure titles a model can only guess at, so a
         * style edit must not be able to remove the instruction that forbids guessing.
         */
        val GUARD = listOf(
            "Reply with exactly the word UNKNOWN if you are not confident you know this",
            "specific book. Never guess from the title alone, and never invent a plot.",
            "Reply with the description only, with no preamble and no commentary."
        ).joinToString("\n")
    }
}

data class OpenRouterConfig(
    val apiKey: String?,
    val model: String = DEFAULT_MODEL
) {
    companion object {
        const val DEFAULT_MODEL = "openrouter/auto"

        fun fromEnvironment(environment: io.ktor.server.application.ApplicationEnvironment): OpenRouterConfig {
            val config = environment.config
            return OpenRouterConfig(
                apiKey = config.propertyOrNull("openrouter.apiKey")
                    ?.getString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                model = config.propertyOrNull("openrouter.model")
                    ?.getString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: DEFAULT_MODEL
            )
        }
    }
}

@Serializable
private data class CompletionRequest(
    val model: String,
    val messages: List<Message>
)

@Serializable
private data class Message(
    val role: String,
    val content: String
)

@Serializable
private data class CompletionResponse(
    val model: String? = null,
    val choices: List<Choice> = emptyList()
)

@Serializable
private data class Choice(
    val message: Message? = null
)

/**
 * The administrator-controlled half of the description prompt. The rule forbidding
 * invented plots lives in [OpenRouterClient.GUARD] and is not represented here.
 */
data class DescriptionPromptSettings(
    val style: String,
    val minLength: Int
) {
    companion object {
        val DEFAULT = DescriptionPromptSettings(
            style = "You describe books for a library catalogue.",
            minLength = 80
        )
    }
}
