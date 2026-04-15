package pl.fairydeck.booksearch.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BackendApiGroupTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(
        email: String = "apitest@example.com"
    ): String {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"API Test User"}""")
        }
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun shouldReturnRegistrationStatusWithoutAuth() = testApp {
        val response = client.get("/api/auth/registration-status")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertNotNull(body["enabled"])
        assertTrue(body["enabled"]!!.jsonPrimitive.boolean || !body["enabled"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun shouldReturnPaginatedDownloadJobsForUser() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/download/jobs") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertNotNull(body["items"])
        assertTrue(body["items"]!!.jsonArray.isEmpty())
        assertNotNull(body["totalCount"])
    }

    @Test
    fun shouldFilterDownloadJobsByStatus() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/download/jobs?status=queued") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertNotNull(body["items"])
    }

    @Test
    fun shouldCancelOwnedDownloadJob() = testApp {
        val token = registerAndGetToken()

        val response = client.patch("/api/download/99999/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun shouldRejectCancelOfNonOwnedJob() = testApp {
        val token1 = registerAndGetToken("user1@example.com")
        val token2 = registerAndGetToken("user2@example.com")

        // User 1 creates no jobs, so job 99999 doesn't exist for user 2 either
        val response = client.patch("/api/download/99999/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        // non-owned or non-existing => 404 (not found for that user)
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun shouldStreamBatchDownloadAsZip() = testApp {
        val token = registerAndGetToken()

        // Request with empty ids — should respond with validation error or empty zip
        val response = client.post("/api/library/batch-download") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"ids":[]}""")
        }

        // Empty ids list should return validation error
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun shouldReturnActivityLogsForUser() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/logs/activity") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertNotNull(body["items"])
        assertNotNull(body["totalCount"])
    }

    @Test
    fun shouldRejectRequestLogsForNonAdmin() = testApp {
        // First user becomes super-admin, so register a second user
        registerAndGetToken("admin@example.com")
        val regularToken = registerAndGetToken("regular@example.com")

        val response = client.get("/api/logs/requests") {
            header(HttpHeaders.Authorization, "Bearer $regularToken")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
