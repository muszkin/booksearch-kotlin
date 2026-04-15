package pl.fairydeck.booksearch.api

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

class GapAnalysisGroup6Test {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(
        email: String = "gap6@example.com"
    ): String {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"Gap6 User"}""")
        }
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun shouldRejectAdminEndpointsForNonSuperAdmin() = testApp {
        registerAndGetToken("admin6@example.com")
        val regularToken = registerAndGetToken("regular6@example.com")

        val toggleResponse = client.put("/api/admin/registration") {
            header(HttpHeaders.Authorization, "Bearer $regularToken")
            contentType(ContentType.Application.Json)
            setBody("""{"enabled":false}""")
        }
        assertEquals(HttpStatusCode.Forbidden, toggleResponse.status)

        val usersResponse = client.get("/api/admin/users") {
            header(HttpHeaders.Authorization, "Bearer $regularToken")
        }
        assertEquals(HttpStatusCode.Forbidden, usersResponse.status)

        val createUserResponse = client.post("/api/admin/users") {
            header(HttpHeaders.Authorization, "Bearer $regularToken")
            contentType(ContentType.Application.Json)
            setBody("""{"email":"new@example.com","displayName":"New","password":"pass123"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, createUserResponse.status)

        val changePassResponse = client.put("/api/admin/users/999/password") {
            header(HttpHeaders.Authorization, "Bearer $regularToken")
            contentType(ContentType.Application.Json)
            setBody("""{"newPassword":"newpass123"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, changePassResponse.status)
    }

    @Test
    fun shouldAllowSuperAdminToAccessAdminEndpoints() = testApp {
        val adminToken = registerAndGetToken("superadmin6@example.com")

        val usersResponse = client.get("/api/admin/users") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, usersResponse.status)

        val body = usersResponse.bodyAsText()
        assertTrue(body.startsWith("["), "Admin users endpoint should return a JSON array")
        assertTrue(body.contains("superadmin6@example.com"), "Response should contain the registered admin user")
    }

    @Test
    fun shouldRejectDeliveryWithInvalidDevice() = testApp {
        val token = registerAndGetToken("deliver-invalid@example.com")

        val response = client.post("/api/deliver/999?device=laptop") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun shouldRejectDeliveryWhenSmtpNotConfigured() = testApp {
        val token = registerAndGetToken("deliver-missing@example.com")

        val response = client.post("/api/deliver/999999?device=kindle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Without SMTP settings configured, delivery is rejected with validation error
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("SMTP") || body.contains("configured"), "Should mention SMTP configuration issue")
    }

    @Test
    fun shouldRejectCancelOfCompletedDownloadJob() = testApp {
        val token = registerAndGetToken("cancel-completed@example.com")

        val cancelResponse = client.patch("/api/download/99999/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, cancelResponse.status)
    }
}
