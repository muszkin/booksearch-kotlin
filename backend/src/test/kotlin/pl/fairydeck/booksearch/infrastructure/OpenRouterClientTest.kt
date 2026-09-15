package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
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
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/chat/completions", request.url.encodedPath)
            requestBody = request.body.toByteArray().decodeToString()
            respond(
                content = ByteReadChannel("""{"choices":[{"message":{"content":"Polski tekst"}}]}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = OpenRouterClient(testConfig(), HttpClient(engine))

        assertEquals("Polski tekst", client.translate("free", "English text").content)
        val body = Json.parseToJsonElement(requestBody).jsonObject
        assertEquals("free", body["model"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("fallback_models"))
        assertEquals(false, body["provider"]?.jsonObject?.get("allow_fallbacks")?.jsonPrimitive?.boolean)

        client.close()
    }

    @Test
    fun `error omits prompt and key`() = runBlocking {
        val client = clientResponding("{\"error\":{\"message\":\"upstream failure\"}}", HttpStatusCode.BadRequest)

        val error = assertThrows(OpenRouterException::class.java) { runBlocking { client.translate("free", "secret prose") } }

        assertFalse(error.message.orEmpty().contains("secret prose"))
        assertFalse(error.message.orEmpty().contains("test-key"))
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

    private fun testConfig() = OpenRouterConfig(apiKey = "test-key", baseUrl = "https://openrouter.test")

    private fun modelsResponse() = """
        {
          "data": [
            {"id":"free","name":"Free text","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0","request":"0"}},
            {"id":"paid","name":"Paid text","architecture":{"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0.0001","request":"0"}},
            {"id":"image","name":"Free image","architecture":{"output_modalities":["image"]},"pricing":{"prompt":"0","completion":"0","request":"0"}}
          ]
        }
    """.trimIndent()
}
