package pl.fairydeck.booksearch.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.request.forms.*
import io.ktor.content.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterClientTest {

    @Test
    fun isDisabledWhenNoApiKeyIsConfigured() = runBlocking {
        var called = false
        val engine = MockEngine { called = true; respondCompletion("anything") }
        val client = OpenRouterClient(config(apiKey = null), HttpClient(engine))

        assertFalse(client.isConfigured)
        assertNull(client.describeBook("Solaris", "Stanisław Lem"))
        assertFalse(called, "no request may be made without a key")
    }

    @Test
    fun returnsTheGeneratedDescription() = runBlocking {
        val engine = MockEngine { respondCompletion(REAL_DESCRIPTION) }

        val summary = OpenRouterClient(config(), HttpClient(engine)).describeBook("Solaris", "Lem")

        assertEquals(REAL_DESCRIPTION, summary)
    }

    @Test
    fun sendsTheConfiguredModelAndBearerToken() = runBlocking {
        var body = ""
        var auth: String? = null
        val engine = MockEngine { request ->
            auth = request.headers[HttpHeaders.Authorization]
            body = (request.body as TextContent).text
            respondCompletion("ok")
        }

        OpenRouterClient(config(), HttpClient(engine)).describeBook("Solaris", "Lem")

        assertEquals("Bearer test-key", auth)
        assertTrue(body.contains("openrouter/auto"), "expected the configured model in $body")
        assertTrue(body.contains("Solaris"))
        assertTrue(body.contains("Lem"))
    }

    @Test
    fun treatsTheAgreedUnknownMarkerAsNoAnswer() = runBlocking {
        val engine = MockEngine { respondCompletion("UNKNOWN") }

        assertNull(OpenRouterClient(config(), HttpClient(engine)).describeBook("Obscure", "Nobody"))
    }

    @Test
    fun rejectsAnAnswerThatAdmitsUncertainty() = runBlocking {
        val engine = MockEngine {
            respondCompletion("I don't have any information about this book.")
        }

        assertNull(OpenRouterClient(config(), HttpClient(engine)).describeBook("Obscure", "Nobody"))
    }

    @Test
    fun rejectsAnAnswerTooShortToBeADescription() = runBlocking {
        val engine = MockEngine { respondCompletion("A novel.") }

        assertNull(OpenRouterClient(config(), HttpClient(engine)).describeBook("Solaris", "Lem"))
    }

    @Test
    fun staysQuietWhenTheServiceFails() = runBlocking {
        val engine = MockEngine { respond("upstream exploded", HttpStatusCode.ServiceUnavailable) }

        assertNull(OpenRouterClient(config(), HttpClient(engine)).describeBook("Solaris", "Lem"))
    }

    private fun MockRequestHandleScope.respondCompletion(content: String) = respond(
        content = """
            {"model":"anthropic/claude-sonnet-4.5",
             "choices":[{"message":{"role":"assistant","content":${quote(content)}}}]}
        """.trimIndent(),
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )

    private fun quote(value: String) = "\"" + value.replace("\"", "\\\"").replace("'", "'") + "\""

    private companion object {
        const val REAL_DESCRIPTION =
            "A philosophical novel about a research station orbiting a sentient ocean. " +
                "The planet answers the scientists by materialising their buried memories, " +
                "and the book turns first contact into a study of human limitation."
    }

    private fun config(apiKey: String? = "test-key") = OpenRouterConfig(
        apiKey = apiKey,
        model = "openrouter/auto"
    )
}
