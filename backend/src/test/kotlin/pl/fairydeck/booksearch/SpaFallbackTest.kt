package pl.fairydeck.booksearch

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SpaFallbackTest {

    @Test
    fun shouldServeFrontendForUnknownNonApiRoute() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val response = client.get("/some/frontend/path")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(
            body.contains("<!DOCTYPE html") || body.contains("<html") || body.contains("index.html"),
            "Non-API route should serve index.html (SPA fallback), got body starting with: ${body.take(200)}"
        )
    }

    @Test
    fun shouldServeHealthEndpointAsApiNotFallback() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.contentType().toString().startsWith("application/json"),
            "API routes should serve JSON, not SPA fallback"
        )
    }
}
