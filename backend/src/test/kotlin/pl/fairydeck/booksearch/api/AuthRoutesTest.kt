package pl.fairydeck.booksearch.api

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
import pl.fairydeck.booksearch.module

class AuthRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    @Test
    fun shouldReturn201WithTokensOnRegister() = testApp {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin@example.com","password":"password123","displayName":"Admin"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        assertTrue(body["refreshToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        assertNotNull(body["user"]?.jsonObject)
        assertEquals("admin@example.com", body["user"]?.jsonObject?.get("email")?.jsonPrimitive?.content)
    }

    @Test
    fun shouldReturn200WithTokensOnLogin() = testApp {
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"password123","displayName":"User"}""")
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"password123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        assertTrue(body["refreshToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
        assertEquals("user@example.com", body["user"]?.jsonObject?.get("email")?.jsonPrimitive?.content)
    }

    @Test
    fun shouldReturn200WithNewAccessTokenOnRefresh() = testApp {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"password123","displayName":"User"}""")
        }
        val registerBody = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        val refreshToken = registerBody["refreshToken"]!!.jsonPrimitive.content

        val response = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["accessToken"]?.jsonPrimitive?.content?.isNotBlank() == true)
    }

    @Test
    fun shouldReturn200OnPasswordResetRequestWithoutLeakingUserExistence() = testApp {
        val nonExistentResponse = client.post("/api/auth/password-reset-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"nonexistent@example.com"}""")
        }
        assertEquals(HttpStatusCode.OK, nonExistentResponse.status)

        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"real@example.com","password":"password123","displayName":"Real"}""")
        }

        val realResponse = client.post("/api/auth/password-reset-request") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"real@example.com"}""")
        }
        assertEquals(HttpStatusCode.OK, realResponse.status)
    }

    @Test
    fun shouldRequireAuthenticationForPasswordChange() = testApp {
        val unauthenticatedResponse = client.put("/api/auth/password") {
            contentType(ContentType.Application.Json)
            setBody("""{"currentPassword":"password123","newPassword":"newpassword456"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, unauthenticatedResponse.status)

        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"password123","displayName":"User"}""")
        }
        val registerBody = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        val accessToken = registerBody["accessToken"]!!.jsonPrimitive.content

        val authenticatedResponse = client.put("/api/auth/password") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody("""{"currentPassword":"password123","newPassword":"newpassword456"}""")
        }
        assertEquals(HttpStatusCode.OK, authenticatedResponse.status)
    }

    @Test
    fun shouldReturn200OnLogoutAndFailSubsequentRefresh() = testApp {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"user@example.com","password":"password123","displayName":"User"}""")
        }
        val registerBody = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        val refreshToken = registerBody["refreshToken"]!!.jsonPrimitive.content
        val accessToken = registerBody["accessToken"]!!.jsonPrimitive.content

        val logoutResponse = client.post("/api/auth/logout") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.OK, logoutResponse.status)

        val refreshResponse = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, refreshResponse.status)
    }

    @Test
    fun meReturns200WithUserForValidBearerToken() = testApp {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"me@example.com","password":"password123","displayName":"Me User"}""")
        }
        val registerBody = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        val accessToken = registerBody["accessToken"]!!.jsonPrimitive.content

        val response = client.get("/api/auth/me") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("me@example.com", body["email"]?.jsonPrimitive?.content)
        assertEquals("Me User", body["displayName"]?.jsonPrimitive?.content)
    }

    @Test
    fun meReturns401WithoutBearerToken() = testApp {
        val response = client.get("/api/auth/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun meReturns401WithExpiredOrInvalidToken() = testApp {
        val response = client.get("/api/auth/me") {
            header(HttpHeaders.Authorization, "Bearer not-a-jwt")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun refreshReturnsRotatedTokenPairAndUser() = testApp {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"rotate@example.com","password":"password123","displayName":"Rotate User"}""")
        }
        val registerBody = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        val originalRefreshToken = registerBody["refreshToken"]!!.jsonPrimitive.content

        val refreshResponse = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$originalRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.OK, refreshResponse.status)

        val refreshBody = json.decodeFromString<JsonObject>(refreshResponse.bodyAsText())
        val newAccessToken = refreshBody["accessToken"]?.jsonPrimitive?.content
        val newRefreshToken = refreshBody["refreshToken"]?.jsonPrimitive?.content

        assertNotNull(newAccessToken)
        assertNotNull(newRefreshToken)
        assertTrue(newAccessToken!!.isNotBlank())
        assertTrue(newRefreshToken!!.isNotBlank())
        assertNotEquals(originalRefreshToken, newRefreshToken)

        val user = refreshBody["user"]?.jsonObject
        assertNotNull(user)
        assertEquals("rotate@example.com", user!!["email"]?.jsonPrimitive?.content)

        val replayResponse = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$originalRefreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, replayResponse.status)
    }
}
