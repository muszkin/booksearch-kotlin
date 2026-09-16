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
            EpubTranslationWorkspace(dir.resolve("jobs")), openRouter, worker, ActivityLogService(ActivityLogRepository(dsl)), retryDelayMillis = 0)
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
        listOf("/api/translation/models", "/api/translation/$sourceId/estimate", "/api/translation/$sourceId/references", "/api/translation/jobs/id/details", "/api/translation/jobs/id/chapters/0/export", "/api/translation/jobs", "/api/translation/jobs/id", "/api/admin/translation/config")
            .forEach { assertEquals(HttpStatusCode.Unauthorized, client.get(it).status) }
        listOf("/api/translation/$sourceId", "/api/translation/$sourceId/context-preview", "/api/translation/$sourceId/references/md5/download", "/api/translation/jobs/id/resume", "/api/translation/jobs/id/cancel")
            .forEach { assertEquals(HttpStatusCode.Unauthorized, client.post(it).status) }
        assertEquals(HttpStatusCode.Unauthorized, client.put("/api/admin/translation/config").status)
    }

    @Test fun `discovers owned active paused and completed jobs for chapter export after reload`() = app {
        val paused = jobs.create(owner, sourceId, "free")
        jobs.markPaused(paused, "server_restart")
        val running = jobs.create(owner, sourceId, "free")
        jobs.markRunning(running)
        val queued = jobs.create(owner, sourceId, "free")
        val cancelled = jobs.create(owner, sourceId, "free")
        jobs.cancel(cancelled)
        val failed = jobs.create(owner, sourceId, "free")
        jobs.markFailed(failed, "workspace_failed")
        val completed = jobs.create(owner, sourceId, "free")
        jobs.markCompleted(completed, sourceId)
        val response = client.get("/api/translation/jobs") { bearerAuth(token()) }
        assertEquals(HttpStatusCode.OK, response.status)
        val found = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(setOf(paused, running, queued, completed), found.map { it.jsonObject["jobId"]!!.jsonPrimitive.content }.toSet())
        assertTrue(found.single { it.jsonObject["jobId"]!!.jsonPrimitive.content == paused }.jsonObject["resumable"]!!.jsonPrimitive.boolean)
        listOf("workspacePath", "sourceFilePath", "sourceBookMd5", "apiKey").forEach { assertFalse(response.bodyAsText().contains(it)) }
        val otherResponse = client.get("/api/translation/jobs") { bearerAuth(token(other)) }
        assertEquals(HttpStatusCode.OK, otherResponse.status)
        assertTrue(Json.parseToJsonElement(otherResponse.bodyAsText()).jsonArray.isEmpty())
    }

    @Test fun `start requires explicit boolean confirmation`() = app {
        listOf("""{"externalProcessingConfirmed":false}""", "{}", """{"externalProcessingConfirmed":"true"}""", "{")
            .forEach { assertEquals(HttpStatusCode.UnprocessableEntity, start(it).status) }
        assertNull(jobs.findActiveBySourceLibraryEntryId(owner, sourceId))
    }

    @Test fun `context preview is owner scoped and validates sampling bounds`() = app {
        val path = "/api/translation/$sourceId/context-preview"
        assertEquals(HttpStatusCode.OK, client.post(path) { bearerAuth(token()); contentType(ContentType.Application.Json); setBody("{}") }.status)
        assertEquals(HttpStatusCode.NotFound, client.post(path) { bearerAuth(token(other)); contentType(ContentType.Application.Json); setBody("{}") }.status)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.post(path) { bearerAuth(token()); contentType(ContentType.Application.Json); setBody("""{"referenceChapters":6}""") }.status)
    }

    @Test fun `diagnostics and chapter export are owner scoped and preserve partial progress`() = app {
        coEvery { openRouter.translate(any(), any()) } answers {
            if (!secondArg<String>().contains("Hello")) throw OpenRouterException("Do not expose provider content", code = "http_400")
            OpenRouterCompletion("""["Cześć ","świecie","!"]""", 0, 0, "actual-free", "stop")
        }
        val response = start("""{"externalProcessingConfirmed":true,"options":{"autoFallback":false}}""")
        val id = Json.parseToJsonElement(response.bodyAsText()).jsonObject["jobId"]!!.jsonPrimitive.content
        val details = client.get("/api/translation/jobs/$id/details") { bearerAuth(token()) }
        assertEquals(HttpStatusCode.OK, details.status)
        val attempts = Json.parseToJsonElement(details.bodyAsText()).jsonObject["attempts"]!!.jsonArray
        val successful = attempts.first().jsonObject
        assertEquals("actual-free", successful["actualModel"]!!.jsonPrimitive.content)
        assertEquals(0, successful["inputTokens"]!!.jsonPrimitive.int)
        assertEquals("completed", successful["status"]!!.jsonPrimitive.content)
        assertFalse(details.bodyAsText().contains("Do not expose"))
        val text = client.get("/api/translation/jobs/$id/chapters/0/export?format=md") { bearerAuth(token()) }
        assertEquals(HttpStatusCode.OK, text.status)
        assertTrue(text.bodyAsText().contains("Cześć świecie!"))
        assertEquals("attachment; filename=chapter-1.md", text.headers[HttpHeaders.ContentDisposition])
        listOf("details", "chapters/0/export").forEach { suffix ->
            assertEquals(HttpStatusCode.NotFound, client.get("/api/translation/jobs/$id/$suffix") { bearerAuth(token(other)) }.status)
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, client.get("/api/translation/jobs/$id/chapters/0/export?format=html") { bearerAuth(token()) }.status)
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
