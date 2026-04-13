package pl.fairydeck.booksearch.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.module

class LibraryRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(
        email: String = "libraryuser@example.com"
    ): String {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"Library User"}""")
        }
        val body = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    private suspend fun ApplicationTestBuilder.seedBook(token: String, md5: String, title: String, author: String) {
        // Search triggers upsert of scraped books into the books table.
        // For library tests we insert directly via the search endpoint cache path,
        // but since we can't easily do that, we use the POST /api/library endpoint
        // which will validate book existence. So we need to seed via direct DB approach.
        // In integration tests with testApplication, the DB is in-memory and shared,
        // so we'll rely on the service layer to handle the "book not found" case.
        // We'll test that scenario explicitly.
    }

    @Test
    fun shouldReturn404WhenAddingNonExistentBookToLibrary() = testApp {
        val token = registerAndGetToken()

        val response = client.post("/api/library") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"bookMd5":"nonexistent000000000000000000001","format":"epub"}""")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun shouldReturn401WithoutAuthentication() = testApp {
        val response = client.get("/api/library")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldReturn200ForEmptyLibrary() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/library") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        assertEquals(0, body["totalItems"]!!.jsonPrimitive.int)
        assertTrue(body["items"]!!.jsonArray.isEmpty())
    }

    @Test
    fun shouldReturn404WhenDeletingNonExistentLibraryEntry() = testApp {
        val token = registerAndGetToken()

        val response = client.delete("/api/library/99999") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun shouldReturnOwnershipCheckForEmptyLibrary() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/library/check?md5=abc123,def456") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        val entries = body.keys
        assertTrue(entries.contains("abc123"))
        assertTrue(entries.contains("def456"))
    }

    @Test
    fun shouldReturn401ForDeleteWithoutAuthentication() = testApp {
        val response = client.delete("/api/library/1")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
