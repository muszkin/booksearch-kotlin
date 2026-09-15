package pl.fairydeck.booksearch.api

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pl.fairydeck.booksearch.configureAuthentication
import pl.fairydeck.booksearch.configureStatusPages
import pl.fairydeck.booksearch.infrastructure.*
import pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS
import pl.fairydeck.booksearch.repository.*
import pl.fairydeck.booksearch.service.*
import java.nio.file.Path
import java.time.Instant

class TranslationRoutesTest {
    @TempDir lateinit var dir: Path
    private val openRouter = mockk<OpenRouterClient>()
    private val worker = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var settings: SystemConfigRepository
    private lateinit var jobs: TranslationJobRepository
    private lateinit var service: TranslationService
    private var owner = 0
    private var other = 0
    private var sourceId = 0

    @BeforeEach fun setup() {
        val dsl = DatabaseFactory.create(dir.resolve("test.db").toString())
        val users = UserRepository(dsl)
        owner = users.create("owner@test.com", "hash", "Owner", false, false).id!!
        other = users.create("other@test.com", "hash", "Other", false, false).id!!
        settings = SystemConfigRepository(dsl).apply { setTranslationDefaultModel("free") }
        jobs = TranslationJobRepository(dsl)
        dsl.insertInto(BOOKS).set(BOOKS.MD5, "source").set(BOOKS.TITLE, "Source")
            .set(BOOKS.LANGUAGE, "en").set(BOOKS.INDEXED_AT, Instant.now().toString()).execute()
        val entries = UserLibraryRepository(dsl)
        sourceId = entries.add(owner, "source", "epub").id!!
        translationFixture(dir.resolve("source.epub"))
        entries.updateFilePath(owner, "source", "epub", "source.epub")
        val library = LibraryService(entries, BookRepository(dsl), ScraperConfig("", "", 0, 1, 1.0, dataPath = dir.toString()), dsl = dsl)
        coEvery { openRouter.listFreeTextModels() } returns listOf(OpenRouterModel("free", "Free model"))
        coEvery { openRouter.translate(any(), any()) } coAnswers { awaitCancellation() }
        service = TranslationService(jobs, TranslationChapterRepository(dsl), settings, library,
            EpubTranslationWorkspace(dir.resolve("jobs")), openRouter, worker, retryDelayMillis = 0)
    }

    @AfterEach fun cleanup() { worker.cancel() }

    private fun token(user: Int = owner, admin: Boolean = false, impersonating: Boolean = false): String =
        JWT.create().withSubject(user.toString()).withIssuer("test").withAudience("test")
            .withClaim("email", "user@test.com").withClaim("is_super_admin", admin)
            .apply { if (impersonating) { withClaim("original_admin_id", 99); withClaim("act_email", "admin@test.com") } }
            .sign(Algorithm.HMAC256("test-secret"))

    private fun app(configured: Boolean = true, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            configureAuthentication("test-secret", "test", "test")
            configureStatusPages()
            routing {
                translationRoutes(service, if (configured) openRouter else null)
                adminRoutes(mockk(), settings, if (configured) openRouter else null)
            }
        }
        block()
    }

    private suspend fun ApplicationTestBuilder.start(body: String = """{"externalProcessingConfirmed":true}""") =
        client.post("/api/translation/$sourceId") { bearerAuth(token()); contentType(ContentType.Application.Json); setBody(body) }

    @Test fun `application registers translation and admin config routes`() = testApplication {
        environment { config = ApplicationConfig("application.yaml") }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/translation/models").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/translation/config").status)
    }

    @Test fun `all translation routes require JWT`() = app {
        listOf("/api/translation/models", "/api/translation/$sourceId/estimate", "/api/translation/jobs/id", "/api/admin/translation/config")
            .forEach { assertEquals(HttpStatusCode.Unauthorized, client.get(it).status) }
        listOf("/api/translation/$sourceId", "/api/translation/jobs/id/resume", "/api/translation/jobs/id/cancel")
            .forEach { assertEquals(HttpStatusCode.Unauthorized, client.post(it).status) }
        assertEquals(HttpStatusCode.Unauthorized, client.put("/api/admin/translation/config").status)
    }

    @Test fun `start requires explicit boolean confirmation`() = app {
        listOf("""{"externalProcessingConfirmed":false}""", "{}", """{"externalProcessingConfirmed":"true"}""", "{")
            .forEach { assertEquals(HttpStatusCode.UnprocessableEntity, start(it).status) }
        assertNull(jobs.findActiveBySourceLibraryEntryId(owner, sourceId))
    }

    @Test fun `estimate start duplicate and status preserve lifecycle contract`() = app {
        val estimate = client.get("/api/translation/$sourceId/estimate") { bearerAuth(token()) }
        assertEquals(HttpStatusCode.OK, estimate.status)
        assertEquals(2, Json.parseToJsonElement(estimate.bodyAsText()).jsonObject["totalChapters"]!!.jsonPrimitive.int)
        val response = start()
        assertEquals(HttpStatusCode.Accepted, response.status)
        val started = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("queued", started["status"]!!.jsonPrimitive.content)
        val id = started["jobId"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.Conflict, start().status)
        val status = client.get("/api/translation/jobs/$id") { bearerAuth(token()) }
        assertEquals(HttpStatusCode.OK, status.status)
        val body = Json.parseToJsonElement(status.bodyAsText()).jsonObject
        assertEquals(sourceId, body["sourceLibraryEntryId"]!!.jsonPrimitive.int)
        assertEquals("free", body["modelId"]!!.jsonPrimitive.content)
        listOf("workspacePath", "sourceFilePath", "apiKey", "Hello").forEach { assertFalse(status.bodyAsText().contains(it)) }
    }

    @Test fun `job and source are hidden from another owner on every operation`() = app {
        val id = jobs.create(owner, sourceId, "free")
        assertEquals(HttpStatusCode.OK, client.get("/api/translation/jobs/$id") { bearerAuth(token()) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/translation/jobs/$id") { bearerAuth(token(other)) }.status)
        listOf("resume", "cancel").forEach {
            assertEquals(HttpStatusCode.NotFound, client.post("/api/translation/jobs/$id/$it") { bearerAuth(token(other)) }.status)
        }
        assertEquals(HttpStatusCode.NotFound, client.get("/api/translation/$sourceId/estimate") { bearerAuth(token(other)) }.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/translation/$sourceId") {
            bearerAuth(token(other)); contentType(ContentType.Application.Json); setBody("""{"externalProcessingConfirmed":true}""")
        }.status)
        assertEquals("queued", jobs.findByIdAndUserId(id, owner)!!.status)
    }

    @Test fun `paused translation resumes and running translation rejects resume or cancellation`() = app {
        coEvery { openRouter.translate(any(), any()) } throws OpenRouterException("upstream secret prose")
        val id = Json.parseToJsonElement(start().bodyAsText()).jsonObject["jobId"]!!.jsonPrimitive.content
        assertEquals("paused", service.status(owner, id).status)
        coEvery { openRouter.translate(any(), any()) } coAnswers { awaitCancellation() }
        assertEquals(HttpStatusCode.Accepted, client.post("/api/translation/jobs/$id/resume") { bearerAuth(token()) }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/api/translation/jobs/$id/resume") { bearerAuth(token()) }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/api/translation/jobs/$id/cancel") { bearerAuth(token()) }.status)
    }

    @Test fun `queued and paused translations can be cancelled`() = app {
        listOf("queued", "paused").forEach { state ->
            val id = jobs.create(owner, sourceId, "free")
            if (state == "paused") jobs.markPaused(id, "test_pause")
            assertEquals(HttpStatusCode.NoContent, client.post("/api/translation/jobs/$id/cancel") { bearerAuth(token()) }.status)
            assertEquals("cancelled", service.status(owner, id).status)
        }
    }

    @Test fun `invalid IDs or unavailable free model return 422`() = app {
        assertEquals(HttpStatusCode.UnprocessableEntity, client.get("/api/translation/invalid/estimate") { bearerAuth(token()) }.status)
        coEvery { openRouter.listFreeTextModels() } returns emptyList()
        assertEquals(HttpStatusCode.UnprocessableEntity, start().status)
    }

    @Test fun `models return safe display metadata and sanitize provider errors`() = app {
        val response = client.get("/api/translation/models") { bearerAuth(token()) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("free", Json.parseToJsonElement(response.bodyAsText()).jsonArray.single().jsonObject["id"]!!.jsonPrimitive.content)
        coEvery { openRouter.listFreeTextModels() } throws OpenRouterException("API-key source prose upstream body")
        val failed = client.get("/api/translation/models") { bearerAuth(token()) }
        assertEquals(HttpStatusCode.UnprocessableEntity, failed.status)
        assertFalse(failed.bodyAsText().contains("API-key"))
        val config = client.get("/api/admin/translation/config") { bearerAuth(token(admin = true)) }
        assertEquals(HttpStatusCode.OK, config.status)
        val body = Json.parseToJsonElement(config.bodyAsText()).jsonObject
        assertEquals("free", body["defaultModelId"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, body["eligible"])
    }

    @Test fun `admin config rejects normal users and impersonating administrators`() = app {
        listOf(token(), token(impersonating = true)).forEach { auth ->
            assertEquals(HttpStatusCode.Forbidden, client.get("/api/admin/translation/config") { bearerAuth(auth) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.put("/api/admin/translation/config") {
                bearerAuth(auth); contentType(ContentType.Application.Json); setBody("""{"defaultModelId":"free"}""")
            }.status)
        }
    }

    @Test fun `super admin config validates live eligibility before saving`() = app {
        suspend fun save(id: String) = client.put("/api/admin/translation/config") {
            bearerAuth(token(admin = true)); contentType(ContentType.Application.Json); setBody("""{"defaultModelId":"$id"}""")
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, save("paid").status)
        assertEquals("free", settings.getTranslationDefaultModel())
        coEvery { openRouter.listFreeTextModels() } returns listOf(OpenRouterModel("new-free", "New free"))
        val saved = save("new-free")
        assertEquals(HttpStatusCode.OK, saved.status)
        assertEquals("new-free", settings.getTranslationDefaultModel())
        val config = client.get("/api/admin/translation/config") { bearerAuth(token(admin = true)) }
        assertEquals(HttpStatusCode.OK, config.status)
        assertTrue(Json.parseToJsonElement(config.bodyAsText()).jsonObject["eligible"]!!.jsonPrimitive.boolean)
    }

    @Test fun `missing credentials are represented without exposing configuration secrets`() = app(configured = false) {
        assertEquals(HttpStatusCode.UnprocessableEntity, client.get("/api/translation/models") { bearerAuth(token()) }.status)
        val response = client.get("/api/admin/translation/config") { bearerAuth(token(admin = true)) }
        assertEquals(HttpStatusCode.OK, response.status)
        val config = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertFalse(config["configured"]!!.jsonPrimitive.boolean)
        assertEquals("free", config["defaultModelId"]!!.jsonPrimitive.content)
        assertFalse(response.bodyAsText().contains("apiKey"))
    }
}
