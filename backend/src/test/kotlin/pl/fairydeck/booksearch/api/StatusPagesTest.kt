package pl.fairydeck.booksearch.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StatusPagesTest {

    @Test
    fun shouldReturnJsonErrorForUnhandledException() = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        routing {
            get("/api/error-trigger") {
                throw RuntimeException("Simulated failure")
            }
        }

        val response = client.get("/api/error-trigger")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertTrue(
            response.contentType().toString().startsWith("application/json"),
            "Error response should be JSON, got: ${response.contentType()}"
        )

        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals(500, body["status"]?.jsonPrimitive?.int)
        assertEquals("Internal server error", body["message"]?.jsonPrimitive?.content)
    }

    @Test
    fun shouldReturnJson404ForUnmatchedApiRoutes() = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }

        val response = client.get("/api/nonexistent-endpoint")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(
            response.contentType().toString().startsWith("application/json"),
            "API 404 should return JSON, got: ${response.contentType()}"
        )

        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals(404, body["status"]?.jsonPrimitive?.int)
    }
}
