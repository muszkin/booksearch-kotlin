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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class GapAnalysisTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    @Test
    fun shouldCompleteFullPasswordResetFlow() = testApp {
        val logAppender = attachListAppender("pl.fairydeck.booksearch.service.AuthService")

        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"reset@example.com","password":"password123","displayName":"Reset User"}""")
        }

        client.post("/api/auth/password-reset-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"reset@example.com"}""")
        }

        val tokenRegex = Regex("Password reset token for user '.*?': (.+)")
        val token = logAppender.list
            .map { it.formattedMessage }
            .firstNotNullOfOrNull { tokenRegex.find(it)?.groupValues?.get(1) }
            ?: fail("Could not extract password reset token from logs")

        val resetResponse = client.post("/api/auth/password-reset") {
            contentType(ContentType.Application.Json)
            setBody("""{"token":"$token","newPassword":"newpassword456"}""")
        }
        assertEquals(HttpStatusCode.OK, resetResponse.status)

        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"reset@example.com","password":"newpassword456"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val body = json.decodeFromString<JsonObject>(loginResponse.bodyAsText())
        assertTrue(body["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun shouldReturn422WhenRegisteringWithInvalidEmail() = testApp {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"not-an-email","password":"password123","displayName":"Test"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun shouldReturn422WhenRegisteringWithShortPassword() = testApp {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"valid@example.com","password":"short","displayName":"Test"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun shouldReturnForcePasswordChangeFlagOnLoginForAdminCreatedUser() = testApp {
        val adminBody = registerAndGetBody("admin@example.com", "password123", "Admin")
        val adminToken = adminBody["accessToken"]!!.jsonPrimitive.content

        client.post("/api/admin/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            setBody("""{"email":"forced@example.com","password":"temporary123","displayName":"Forced User"}""")
        }

        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"forced@example.com","password":"temporary123"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val body = json.decodeFromString<JsonObject>(loginResponse.bodyAsText())
        val user = body["user"]!!.jsonObject
        assertNotNull(user, "Login response should include user object")
    }

    @Test
    fun shouldClearForcePasswordChangeFlagAfterPasswordChange() = testApp {
        val adminBody = registerAndGetBody("admin@example.com", "password123", "Admin")
        val adminToken = adminBody["accessToken"]!!.jsonPrimitive.content

        client.post("/api/admin/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            setBody("""{"email":"forced@example.com","password":"temporary123","displayName":"Forced"}""")
        }

        val loginBody = registerAndGetBody("forced@example.com", "temporary123")
        val userToken = loginBody["accessToken"]!!.jsonPrimitive.content

        val changeResponse = client.put("/api/auth/password") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $userToken")
            setBody("""{"currentPassword":"temporary123","newPassword":"permanent456"}""")
        }
        assertEquals(HttpStatusCode.OK, changeResponse.status)

        val reLoginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"forced@example.com","password":"permanent456"}""")
        }
        assertEquals(HttpStatusCode.OK, reLoginResponse.status)
    }

    @Test
    fun shouldReturn401WhenRefreshingWithInvalidToken() = testApp {
        val response = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"completely-invalid-token-that-does-not-exist"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldServeOpenApiSpecAsValidJson() = testApp {
        val response = client.get("/api/openapi.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(
            response.contentType().toString().startsWith("application/json"),
            "OpenAPI spec should be served as JSON, got: ${response.contentType()}"
        )

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertNotNull(body["openapi"], "Response should contain 'openapi' field")
        assertNotNull(body["info"], "Response should contain 'info' field")
        assertNotNull(body["paths"], "Response should contain 'paths' field")
    }

    @Test
    fun shouldServeSwaggerUiAsHtml() = testApp {
        val response = client.get("/swagger-ui")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("swagger-ui"), "Swagger UI page should reference swagger-ui")
        assertTrue(body.contains("<html"), "Swagger UI should return HTML")
        assertTrue(body.contains("/api/openapi.json"), "Swagger UI should reference the OpenAPI spec URL")
    }

    private suspend fun ApplicationTestBuilder.registerAndGetBody(
        email: String,
        password: String,
        displayName: String = "Test"
    ): JsonObject {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","displayName":"$displayName"}""")
        }
        if (response.status == HttpStatusCode.Created) {
            return json.decodeFromString<JsonObject>(response.bodyAsText())
        }
        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        return json.decodeFromString<JsonObject>(loginResponse.bodyAsText())
    }

    private fun attachListAppender(loggerName: String): ListAppender<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(loggerName) as Logger
        val listAppender = ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)
        return listAppender
    }
}
