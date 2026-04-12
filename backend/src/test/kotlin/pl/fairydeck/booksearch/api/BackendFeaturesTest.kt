package pl.fairydeck.booksearch.api

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class BackendFeaturesTest {

    @Test
    fun shouldReturnOkStatusFromHealthEndpoint() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("ok", body["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun shouldReturnJsonContentTypeFromHealthEndpoint() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val response = client.get("/api/health")
        val contentType = response.contentType()
        assertTrue(
            contentType.toString().startsWith("application/json"),
            "Expected application/json but got: $contentType"
        )
    }

    @Test
    fun shouldReflectXRequestIdFromRequest() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val requestId = "test-request-id-12345"
        val response = client.get("/api/health") {
            header("X-Request-Id", requestId)
        }

        assertEquals(requestId, response.headers["X-Request-Id"])
    }

    @Test
    fun shouldGenerateXRequestIdWhenNotProvided() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }

        val response = client.get("/api/health")
        val generatedId = response.headers["X-Request-Id"]

        assertNotNull(generatedId, "X-Request-Id should be auto-generated")
        assertTrue(generatedId!!.isNotBlank(), "X-Request-Id should not be blank")
    }

    @Test
    fun shouldLogRequestMethodPathAndStatusCode() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }
        val logEvents = attachListAppender("pl.fairydeck.booksearch.infrastructure.RequestLoggerPlugin")

        client.get("/api/health")

        val messages = logEvents.list.map { it.formattedMessage }
        assertTrue(messages.any { it.contains("GET") }, "Log should contain HTTP method GET, got: $messages")
        assertTrue(messages.any { it.contains("/api/health") }, "Log should contain request path, got: $messages")
        assertTrue(messages.any { it.contains("200") }, "Log should contain status code 200, got: $messages")
    }

    @Test
    fun shouldIncludeXRequestIdInLogMdc() = testApplication {
        environment { config = io.ktor.server.config.ApplicationConfig("application.yaml") }
        val logEvents = attachListAppender("pl.fairydeck.booksearch.infrastructure.RequestLoggerPlugin")

        val requestId = "log-test-request-id-67890"
        client.get("/api/health") {
            header("X-Request-Id", requestId)
        }

        val mdcValues = logEvents.list.flatMap { it.mdcPropertyMap.values }
        assertTrue(
            mdcValues.any { it == requestId },
            "MDC should contain x-request-id=$requestId, got MDC values: $mdcValues"
        )
    }

    private fun attachListAppender(loggerName: String): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(loggerName) as Logger
        val listAppender = ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)
        return listAppender
    }
}
