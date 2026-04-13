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

class SearchRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(): String {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"searcher@example.com","password":"password123","displayName":"Searcher"}""")
        }
        val body = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun shouldReturn400WhenQueryParamMissing() = testApp {
        val token = registerAndGetToken()

        val response = client.get("/api/search") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun shouldReturn401WithoutJwtToken() = testApp {
        val response = client.get("/api/search?q=test")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
