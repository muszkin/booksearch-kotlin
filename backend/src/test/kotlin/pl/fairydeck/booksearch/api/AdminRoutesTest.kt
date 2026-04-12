package pl.fairydeck.booksearch.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AdminRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerUser(
        email: String,
        password: String,
        displayName: String
    ): JsonObject {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","displayName":"$displayName"}""")
        }
        return json.decodeFromString<JsonObject>(response.bodyAsText())
    }

    private fun JsonObject.accessToken(): String =
        this["accessToken"]!!.jsonPrimitive.content

    @Test
    fun shouldReturn200WhenSuperAdminTogglesRegistration() = testApp {
        val adminBody = registerUser("admin@example.com", "password123", "Admin")
        val token = adminBody.accessToken()

        val response = client.put("/api/admin/registration") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"enabled":false}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun shouldReturn403WhenRegularUserTogglesRegistration() = testApp {
        registerUser("admin@example.com", "password123", "Admin")
        val regularBody = registerUser("regular@example.com", "password123", "Regular")
        val token = regularBody.accessToken()

        val response = client.put("/api/admin/registration") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"enabled":false}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun shouldReturn201WhenSuperAdminCreatesUser() = testApp {
        val adminBody = registerUser("admin@example.com", "password123", "Admin")
        val token = adminBody.accessToken()

        val response = client.post("/api/admin/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"email":"newuser@example.com","password":"password123","displayName":"New User"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals("newuser@example.com", body["email"]?.jsonPrimitive?.content)
        assertEquals("New User", body["displayName"]?.jsonPrimitive?.content)
        assertFalse(body["isSuperAdmin"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun shouldReturn403WhenRegularUserCreatesUser() = testApp {
        registerUser("admin@example.com", "password123", "Admin")
        val regularBody = registerUser("regular@example.com", "password123", "Regular")
        val token = regularBody.accessToken()

        val response = client.post("/api/admin/users") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"email":"newuser@example.com","password":"password123","displayName":"New User"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun shouldReturn200WhenSuperAdminChangesUserPassword() = testApp {
        val adminBody = registerUser("admin@example.com", "password123", "Admin")
        val adminToken = adminBody.accessToken()

        val regularBody = registerUser("regular@example.com", "password123", "Regular")
        val regularUserId = regularBody["user"]!!.jsonObject["id"]!!.jsonPrimitive.content

        val response = client.put("/api/admin/users/$regularUserId/password") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $adminToken")
            setBody("""{"newPassword":"changedpassword123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val loginResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"regular@example.com","password":"changedpassword123"}""")
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)
    }
}
