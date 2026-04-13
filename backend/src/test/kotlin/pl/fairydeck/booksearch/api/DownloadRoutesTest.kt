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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.module

class DownloadRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(
        email: String = "downloaduser@example.com"
    ): String {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"Download User"}""")
        }
        val body = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun shouldReturn401WhenPostDownloadWithoutAuth() = testApp {
        val response = client.post("/api/download/abc123def456abc123def456abc123de")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldReturn404WhenBookNotFoundForDownload() = testApp {
        val token = registerAndGetToken()

        val response = client.post("/api/download/nonexistent000000000000000000001") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun shouldReturn401WhenGetDownloadStatusWithoutAuth() = testApp {
        val response = client.get("/api/download/status/1")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldReturn404WhenDownloadStatusNotFoundForUser() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/download/status/99999") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun shouldReturn404WhenLibraryFileDoesNotExist() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/library/99999/file") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
