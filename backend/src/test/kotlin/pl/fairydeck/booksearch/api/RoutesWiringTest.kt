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

class RoutesWiringTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(
        email: String = "wiring-test@example.com"
    ): String {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"Wiring Tester"}""")
        }
        val body = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun shouldReturn401WhenAccessingConvertWithoutAuth() = testApp {
        val response = client.post("/api/convert/1?target=mobi")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldReturn401WhenAccessingDeliverWithoutAuth() = testApp {
        val response = client.post("/api/deliver/1?device=kindle")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldReturn401WhenAccessingSettingsWithoutAuth() = testApp {
        val response = client.get("/api/settings")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldReturn401WhenAccessingDeliveriesWithoutAuth() = testApp {
        val response = client.get("/api/deliveries")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldReturn401WhenAccessingConversionStatusWithoutAuth() = testApp {
        val response = client.get("/api/convert/status/some-job-id")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldAccessConvertRouteWithValidAuth() = testApp {
        val token = registerAndGetToken("convert-auth@example.com")

        val response = client.post("/api/convert/999?target=mobi") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        // Should not be 401 - might be 404 (entry not found) or 422 (validation) but not unauthorized
        assertNotEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldAccessDeliverRouteWithValidAuth() = testApp {
        val token = registerAndGetToken("deliver-auth@example.com")

        val response = client.post("/api/deliver/999?device=kindle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertNotEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun shouldAccessSettingsRouteWithValidAuth() = testApp {
        val token = registerAndGetToken("settings-auth@example.com")

        val response = client.get("/api/settings") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun shouldIncludeConvertEndpointsInOpenApiSpec() = testApp {
        val response = client.get("/api/openapi.json")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        val paths = body["paths"]!!.jsonObject

        assertTrue(paths.containsKey("/api/convert/{libraryId}"),
            "OpenAPI spec should include /api/convert/{libraryId}, found paths: ${paths.keys}")
        assertTrue(paths.containsKey("/api/convert/status/{jobId}"),
            "OpenAPI spec should include /api/convert/status/{jobId}")
    }

    @Test
    fun shouldIncludeDeliverEndpointsInOpenApiSpec() = testApp {
        val response = client.get("/api/openapi.json")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        val paths = body["paths"]!!.jsonObject

        assertTrue(paths.containsKey("/api/deliver/{libraryId}"),
            "OpenAPI spec should include /api/deliver/{libraryId}")
        assertTrue(paths.containsKey("/api/deliveries"),
            "OpenAPI spec should include /api/deliveries")
    }

    @Test
    fun shouldIncludeSettingsEndpointsInOpenApiSpec() = testApp {
        val response = client.get("/api/openapi.json")
        assertEquals(HttpStatusCode.OK, response.status)

        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        val paths = body["paths"]!!.jsonObject

        assertTrue(paths.containsKey("/api/settings"),
            "OpenAPI spec should include /api/settings")
        assertTrue(paths.containsKey("/api/settings/{device}"),
            "OpenAPI spec should include /api/settings/{device}")
    }
}
