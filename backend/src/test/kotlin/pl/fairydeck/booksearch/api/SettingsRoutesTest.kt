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

class SettingsRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        environment {
            config = ApplicationConfig("application.yaml")
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.registerUser(
        email: String,
        password: String = "password123",
        displayName: String = "Test User"
    ): String {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password","displayName":"$displayName"}""")
        }
        val body = json.decodeFromString<JsonObject>(response.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }

    @Test
    fun shouldSaveAndReturnKindleSettingsWithPasswordRedacted() = testApp {
        val token = registerUser("kindle@example.com")

        val putResponse = client.put("/api/settings/kindle") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"host":"smtp.gmail.com","port":587,"username":"user@gmail.com","password":"secret123","fromEmail":"user@gmail.com","recipientEmail":"kindle@kindle.com"}""")
        }
        assertEquals(HttpStatusCode.OK, putResponse.status)

        val getResponse = client.get("/api/settings/kindle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)

        val body = json.decodeFromString<JsonObject>(getResponse.bodyAsText())
        assertEquals("smtp.gmail.com", body["host"]?.jsonPrimitive?.content)
        assertEquals("587", body["port"]?.jsonPrimitive?.content)
        assertEquals("user@gmail.com", body["username"]?.jsonPrimitive?.content)
        assertEquals("********", body["password"]?.jsonPrimitive?.content)
        assertEquals("user@gmail.com", body["fromEmail"]?.jsonPrimitive?.content)
        assertEquals("kindle@kindle.com", body["recipientEmail"]?.jsonPrimitive?.content)
    }

    @Test
    fun shouldUpsertExistingSettings() = testApp {
        val token = registerUser("upsert@example.com")

        client.put("/api/settings/kindle") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"host":"old-host.com","port":25,"username":"old","password":"old","fromEmail":"old@mail.com","recipientEmail":"old@kindle.com"}""")
        }

        client.put("/api/settings/kindle") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"host":"new-host.com","port":587,"username":"new","password":"new","fromEmail":"new@mail.com","recipientEmail":"new@kindle.com"}""")
        }

        val getResponse = client.get("/api/settings/kindle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        val body = json.decodeFromString<JsonObject>(getResponse.bodyAsText())
        assertEquals("new-host.com", body["host"]?.jsonPrimitive?.content)
        assertEquals("new@kindle.com", body["recipientEmail"]?.jsonPrimitive?.content)
    }

    @Test
    fun shouldIsolateSettingsBetweenUsers() = testApp {
        val token1 = registerUser("user1@example.com")
        val token2 = registerUser("user2@example.com")

        client.put("/api/settings/kindle") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token1")
            setBody("""{"host":"host1.com","port":587,"username":"u1","password":"p1","fromEmail":"u1@mail.com","recipientEmail":"u1@kindle.com"}""")
        }

        val getResponse = client.get("/api/settings/kindle") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun shouldRejectInvalidDevice() = testApp {
        val token = registerUser("invalid-device@example.com")

        val response = client.put("/api/settings/laptop") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"host":"h","port":587,"username":"u","password":"p","fromEmail":"f@m.com","recipientEmail":"r@k.com"}""")
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
    }

    @Test
    fun shouldReturnAllDeviceSettingsFromGetAll() = testApp {
        val token = registerUser("all-settings@example.com")

        client.put("/api/settings/kindle") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"host":"kindle-host.com","port":587,"username":"ku","password":"kp","fromEmail":"k@m.com","recipientEmail":"k@kindle.com"}""")
        }

        client.put("/api/settings/pocketbook") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"host":"pb-host.com","port":465,"username":"pu","password":"pp","fromEmail":"p@m.com","recipientEmail":"p@pocketbook.com"}""")
        }

        val getResponse = client.get("/api/settings") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, getResponse.status)

        val body = json.decodeFromString<JsonObject>(getResponse.bodyAsText())
        assertNotNull(body["kindle"])
        assertNotNull(body["pocketbook"])

        val kindleSettings = body["kindle"]!!.jsonObject
        assertEquals("kindle-host.com", kindleSettings["host"]?.jsonPrimitive?.content)
        assertEquals("********", kindleSettings["password"]?.jsonPrimitive?.content)

        val pbSettings = body["pocketbook"]!!.jsonObject
        assertEquals("pb-host.com", pbSettings["host"]?.jsonPrimitive?.content)
        assertEquals("********", pbSettings["password"]?.jsonPrimitive?.content)
    }
}
