package pl.fairydeck.booksearch

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ApplicationTest {

    @Test
    fun shouldStartWithoutErrors() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun shouldSerializeJsonResponsesViaContentNegotiation() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val response = client.get("/api/health")
        val contentType = response.contentType()
        assertTrue(
            contentType.toString().startsWith("application/json"),
            "Expected application/json content type but got: $contentType"
        )

        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun shouldLoadApplicationConfiguration() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertNotNull(body["status"], "Health endpoint should return status field from configured module")
    }
}
