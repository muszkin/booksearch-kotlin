package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OpenRouterClientTest {

    @Test fun `completion returns actual usage and rate limit is retryable`() = runBlocking {
        var limited = false
        val client = OpenRouterClient(testConfig(), HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/models") respondJson(modelsResponse())
            else if (limited) respondJson("{}", HttpStatusCode.TooManyRequests)
            else respondJson("""{"choices":[{"message":{"content":"translated"}}],"usage":{"prompt_tokens":23,"completion_tokens":17}}""")
        }))
        val result = client.translate("free", "source")
        assertEquals(23, result.inputTokens)
        assertEquals(17, result.outputTokens)
        limited = true
        val failure = assertThrows(OpenRouterException::class.java) { runBlocking { client.translate("free", "source") } }
        assertEquals(true, failure.retryable)
        client.close()
    }

    @Test
    fun `filters paid and non-text models`() = runBlocking {
        val client = clientResponding(modelsResponse())

        assertEquals(listOf("free"), client.listFreeTextModels().map { it.id })

        client.close()
    }

    @Test
    fun `translate sends the requested model without fallback`() = runBlocking {
        var requestBody = ""
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/models" -> respondJson(modelsResponse())
                "/api/v1/chat/completions" -> {
                    assertEquals(HttpMethod.Post, request.method)
                    requestBody = request.body.toByteArray().decodeToString()
                    respondJson("""{"choices":[{"message":{"content":"Polski tekst"}}]}""")
                }
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
        }
        val client = OpenRouterClient(testConfig(), HttpClient(engine))

        assertEquals("Polski tekst", client.translate("free", "English text").content)
        val body = Json.parseToJsonElement(requestBody).jsonObject
        assertEquals("free", body["model"]?.jsonPrimitive?.content)
        assertEquals(Json.parseToJsonElement("""[{"role":"user","content":"English text"}]"""), body["messages"])
        assertFalse(body.containsKey("fallback_models"))
        assertEquals(false, body["provider"]?.jsonObject?.get("allow_fallbacks")?.jsonPrimitive?.boolean)

        client.close()
    }

    @Test
    fun `error omits prompt and key`() = runBlocking {
        val client = OpenRouterClient(testConfig(), HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/models" -> respondJson(modelsResponse())
                "/api/v1/chat/completions" -> respondJson("{\"error\":{\"message\":\"upstream failure\"}}", HttpStatusCode.BadRequest)
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
        }))

        val error = assertThrows(OpenRouterException::class.java) { runBlocking { client.translate("free", "secret prose") } }

        assertFalse(error.message.orEmpty().contains("secret prose"))
        assertFalse(error.message.orEmpty().contains("test-key"))
        client.close()
    }

    @Test
    fun `translate rejects a paid model before completion`() = runBlocking {
        var completionRequested = false
        val client = OpenRouterClient(testConfig(), HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/api/v1/models" -> respondJson(modelsResponse())
                "/api/v1/chat/completions" -> {
                    completionRequested = true
                    respondJson("""{"choices":[{"message":{"content":"should not happen"}}]}""")
                }
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
        }))

        assertThrows(OpenRouterException::class.java) { runBlocking { client.translate("paid-prompt", "secret prose") } }
        assertFalse(completionRequested)

        client.close()
    }

    private fun clientResponding(body: String, status: HttpStatusCode = HttpStatusCode.OK): OpenRouterClient =
        OpenRouterClient(
            testConfig(),
            HttpClient(MockEngine {
                respond(
                    content = ByteReadChannel(body),
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            })
        )

    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun testConfig() = OpenRouterConfig(apiKey = "test-key", baseUrl = "https://openrouter.test")

    private fun modelsResponse() = """
        {
          "data": [
            {"id":"free","name":"Free text","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0","request":"0"}},
            {"id":"paid","name":"Paid text","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0.0001","request":"0"}},
            {"id":"paid-prompt","name":"Paid prompt","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0.0001","completion":"0","request":"0"}},
            {"id":"paid-request","name":"Paid request","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0","request":"0.0001"}},
            {"id":"missing-price","name":"Missing price","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0"}},
            {"id":"invalid-price","name":"Invalid price","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"free","completion":"0","request":"0"}},
            {"id":"image","name":"Free image","architecture":{"output_modalities":["image"]},"pricing":{"prompt":"0","completion":"0","request":"0"}}
          ]
        }
    """.trimIndent()
}
