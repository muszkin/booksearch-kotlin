package pl.fairydeck.booksearch.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.module

class AsyncSearchRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(email: String): String {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"Searcher"}""")
        }
        val body = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun submittingSearchReturnsAcceptedWithJobId() = testApp {
        val token = registerAndGetToken("async-accept@example.com")

        val response = client.post("/api/search?q=lem") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertTrue(body["jobId"]!!.jsonPrimitive.int > 0)
        assertEquals("queued", body["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun submittedJobIsPollableByItsOwner() = testApp {
        val token = registerAndGetToken("async-poll@example.com")
        val submitResponse = client.post("/api/search?q=lem") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val jobId = json.decodeFromString<JsonObject>(submitResponse.bodyAsText())["jobId"]!!.jsonPrimitive.int

        val statusResponse = client.get("/api/search/status/$jobId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, statusResponse.status)
        val body = json.decodeFromString<JsonObject>(statusResponse.bodyAsText())
        assertEquals(jobId, body["jobId"]!!.jsonPrimitive.int)
        assertTrue(body["status"]!!.jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun submittingSearchWithoutQueryReturnsBadRequest() = testApp {
        val token = registerAndGetToken("async-noquery@example.com")

        val response = client.post("/api/search") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun statusOfUnknownJobReturnsNotFound() = testApp {
        val token = registerAndGetToken("async-unknown@example.com")

        val response = client.get("/api/search/status/999999") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
