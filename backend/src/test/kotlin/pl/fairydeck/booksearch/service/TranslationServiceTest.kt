package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.*
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pl.fairydeck.booksearch.api.*
import pl.fairydeck.booksearch.infrastructure.*
import pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS
import pl.fairydeck.booksearch.repository.*
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeoutException

class TranslationServiceTest {
    @TempDir lateinit var dir: Path
    lateinit var dsl: DSLContext
    lateinit var library: LibraryService
    lateinit var entries: UserLibraryRepository
    lateinit var jobs: TranslationJobRepository
    lateinit var chapters: TranslationChapterRepository
    lateinit var settings: SystemConfigRepository
    val client = mockk<OpenRouterClient>()
    var owner = 0
    var entryId = 0

    @BeforeEach fun setup() {
        dsl = DatabaseFactory.create(dir.resolve("test.db").toString())
        owner = UserRepository(dsl).create("owner@test.com", "hash", "Owner", false, false).id!!
        entries = UserLibraryRepository(dsl)
        jobs = TranslationJobRepository(dsl)
        chapters = TranslationChapterRepository(dsl)
        settings = SystemConfigRepository(dsl).apply { setTranslationDefaultModel("free") }
        dsl.insertInto(BOOKS).set(BOOKS.MD5, "source").set(BOOKS.TITLE, "Source").set(BOOKS.LANGUAGE, "en")
            .set(BOOKS.AUTHOR, "Author").set(BOOKS.INDEXED_AT, Instant.now().toString()).execute()
        entryId = entries.add(owner, "source", "epub").id!!
        translationFixture(dir.resolve("source.epub"))
        entries.updateFilePath(owner, "source", "epub", "source.epub")
        library = LibraryService(entries, BookRepository(dsl), ScraperConfig("", "", 0, 1, 1.0, dataPath = dir.toString()), dsl = dsl)
        coEvery { client.listFreeTextModels() } returns listOf(OpenRouterModel("free", "Free"))
        coEvery { client.translate(any(), any()) } answers {
            OpenRouterCompletion(if (secondArg<String>().contains("Hello")) """["Cześć ","świecie","!"]""" else """["Drugi"]""", 12, 9)
        }
    }

    private fun service(scope: CoroutineScope) = TranslationService(jobs, chapters, settings, library, EpubTranslationWorkspace(dir.resolve("jobs")), client, scope, ActivityLogService(ActivityLogRepository(dsl)), retryDelayMillis = 0)
    private suspend fun awaitStopped(service: TranslationService, id: String) {
        withTimeout(5000) { while (service.status(owner, id).status in listOf("queued", "running")) yield() }
    }

    @Test fun `fallback records actual model and validation reason then completes without paid calls`() = runBlocking {
        coEvery { client.listFreeTextModels() } returns listOf(OpenRouterModel("free", "Router"), OpenRouterModel("next-free", "Next"))
        coEvery { client.translate("free", any()) } returns OpenRouterCompletion("[\"wrong count\"]", 5, 7, "resolved-model", "stop")
        coEvery { client.translate("next-free", any()) } answers {
            OpenRouterCompletion(if (secondArg<String>().contains("Hello")) """["Cześć ","świecie","!"]""" else """["Drugi"]""", 2, 3, "next-free", "stop")
        }
        val service = service(this)
        val id = service.start(owner, entryId, true).jobId
        awaitStopped(service, id)
        assertEquals("completed", service.status(owner, id).status)
        val details = service.details(owner, id)
        assertTrue(details.attempts.any { it.errorCode == "item_count_mismatch" && it.actualModel == "resolved-model" })
        assertTrue(details.attempts.any { it.requestedModel == "next-free" && it.status == "completed" })
        assertTrue(service.exportChapter(owner, id, 0, "txt").contains("Cześć świecie!"))
        assertThrows(NotFoundException::class.java) { service.details(owner + 1, id) }
        assertThrows(NotFoundException::class.java) { service.exportChapter(owner + 1, id, 0, "txt") }
    }

    @Test fun `resume can change model and glossary while preserving completed chapter bytes`() = runBlocking {
        coEvery { client.translate(any(), any()) } answers {
            if (!secondArg<String>().contains("Hello")) throw OpenRouterException("failure", code = "http_400")
            OpenRouterCompletion("""["Cześć ","świecie","!"]""")
        }
        val service = service(this)
        val id = service.start(owner, entryId, true, TranslationOptions(autoFallback = false)).jobId
        awaitStopped(service, id)
        assertEquals(1, service.status(owner, id).completedChapters)
        val before = dir.resolve("jobs/$id/0-0.json").toFile().readBytes()
        coEvery { client.listFreeTextModels() } returns listOf(OpenRouterModel("next-free", "Next"))
        coEvery { client.translate("next-free", any()) } answers {
            assertTrue(secondArg<String>().contains("Conjoiners → Spójni"))
            OpenRouterCompletion("""["Drugi"]""")
        }
        service.resume(owner, id, TranslationOptions(modelId = "next-free", glossary = "Conjoiners → Spójni"))
        awaitStopped(service, id)
        assertEquals("completed", service.status(owner, id).status)
        assertArrayEquals(before, dir.resolve("jobs/$id/0-0.json").toFile().readBytes())
    }

    @Test fun `references are owner scoped Polish same author EPUBs and sampled context is persisted`() = runBlocking {
        translationFixture(dir.resolve("reference.epub"), "<p>Polskie nazewnictwo</p>", language = "pl")
        dsl.insertInto(BOOKS).set(BOOKS.MD5, "reference").set(BOOKS.TITLE, "Referencja").set(BOOKS.LANGUAGE, "Polish [pl]")
            .set(BOOKS.AUTHOR, "Author").set(BOOKS.INDEXED_AT, Instant.now().toString()).execute()
        val reference = entries.add(owner, "reference", "epub").id!!
        entries.updateFilePath(owner, "reference", "epub", "reference.epub")
        val service = service(this)
        assertEquals(listOf(reference), service.references(owner, entryId).map { it.id })
        val preview = service.previewContext(owner, entryId, TranslationOptions(referenceLibraryIds = listOf(reference), referenceChapters = 2))
        val id = service.start(owner, entryId, true, TranslationOptions(referenceLibraryIds = listOf(reference), referenceChapters = 2)).jobId
        awaitStopped(service, id)
        val options = service.details(owner, id).options
        assertTrue(options.referenceText.contains("Polskie nazewnictwo"))
        assertEquals(preview.referenceText, options.referenceText)
        assertEquals(options, EpubTranslationWorkspace(dir.resolve("jobs")).options(id))
        dsl.update(BOOKS).set(BOOKS.AUTHOR, "Different Author").where(BOOKS.MD5.eq("reference")).execute()
        assertTrue(service.references(owner, entryId).none { it.id == reference })
    }

    @Test fun `reference download checks owner author language and format before acquisition`() {
        dsl.insertInto(BOOKS).set(BOOKS.MD5, "candidate").set(BOOKS.TITLE, "Reference")
            .set(BOOKS.AUTHOR, "Author").set(BOOKS.LANGUAGE, "pl").set(BOOKS.FORMAT, "epub")
            .set(BOOKS.INDEXED_AT, Instant.now().toString()).execute()
        library.validateTranslationReferenceDownload(owner, entryId, "candidate")
        assertThrows(NotFoundException::class.java) { library.validateTranslationReferenceDownload(owner + 1, entryId, "candidate") }
        dsl.update(BOOKS).set(BOOKS.LANGUAGE, "en").where(BOOKS.MD5.eq("candidate")).execute()
        assertThrows(ValidationException::class.java) { library.validateTranslationReferenceDownload(owner, entryId, "candidate") }
        dsl.update(BOOKS).set(BOOKS.LANGUAGE, "pl").set(BOOKS.FORMAT, "pdf").where(BOOKS.MD5.eq("candidate")).execute()
        assertThrows(ValidationException::class.java) { library.validateTranslationReferenceDownload(owner, entryId, "candidate") }
        dsl.update(BOOKS).set(BOOKS.FORMAT, "epub").set(BOOKS.AUTHOR, "Someone Else").where(BOOKS.MD5.eq("candidate")).execute()
        assertThrows(ValidationException::class.java) { library.validateTranslationReferenceDownload(owner, entryId, "candidate") }
    }

    @Test
    fun `recognizes catalog English language labels`() {
        assertTrue(isEnglish("English [en]"))
        assertFalse(isEnglish("Englishish"))
    }

    @Test fun `explicit free model can replace an unavailable administrator default`() = runBlocking {
        settings.setTranslationDefaultModel("unavailable")
        val service = service(this)
        val id = service.start(owner, entryId, true, TranslationOptions(modelId = "free")).jobId
        awaitStopped(service, id)
        assertEquals("completed", service.status(owner, id).status)
    }

    @Test fun `third transient failure pauses and resume publishes separate Polish entry`() = runBlocking {
        var failures = true
        coEvery { client.translate(any(), any()) } answers {
            if (failures) throw TimeoutException("secret prose")
            OpenRouterCompletion(if (secondArg<String>().contains("Hello")) """["Cześć ","świecie","!"]""" else """["Drugi"]""", 12, 9)
        }
        val service = service(this)
        val id = service.start(owner, entryId, true).jobId
        awaitStopped(service, id)
        assertEquals("paused", service.status(owner, id).status)
        assertEquals(3, chapters.findByJobId(id).first().attempts)
        assertFalse(service.status(owner, id).error.orEmpty().contains("secret"))
        assertEquals(1, entries.countByUserId(owner))
        failures = false
        service.resume(owner, id)
        awaitStopped(service, id)
        val status = service.status(owner, id)
        assertEquals("completed", status.status)
        assertEquals(24, status.actualInputTokens)
        assertEquals(18, status.actualOutputTokens)
        assertEquals(2, entries.countByUserId(owner))
        val output = entries.findByIdAndUserId(status.outputLibraryEntryId!!, owner)!!
        assertEquals("pl", BookRepository(dsl).findByMd5(output.bookMd5!!)!!.language)
        assertNotEquals(entryId, output.id)
        assertTrue(java.io.File(library.getFileForEntry(owner, output.id!!).absolutePath).exists())
        assertEquals("source.epub", entries.findByIdAndUserId(entryId, owner)!!.filePath)
    }

    @Test fun `audits confirmed start and committed publication with identifiers only`() = runBlocking {
        val service = service(this)
        assertThrows(ValidationException::class.java) { runBlocking { service.start(owner, entryId, false) } }
        val audit = ActivityLogRepository(dsl)
        assertTrue(audit.findByUserId(owner, 1, 100).items.isEmpty())
        val id = service.start(owner, entryId, true).jobId
        awaitStopped(service, id)
        val events = audit.findByUserId(owner, 1, 100).items
        assertEquals(setOf("TRANSLATION_STARTED", "TRANSLATION_COMPLETED"), events.map { it.actionType }.toSet())
        assertEquals(2, events.size)
        events.forEach {
            assertEquals("translation_job", it.entityType)
            assertEquals(id, it.entityId)
        }
        assertEquals("sourceLibraryEntryId=$entryId", events.single { it.actionType == "TRANSLATION_STARTED" }.details)
        assertEquals("outputLibraryEntryId=${service.status(owner, id).outputLibraryEntryId}", events.single { it.actionType == "TRANSLATION_COMPLETED" }.details)
    }

    @Test fun `failed publication does not emit completion audit and resume emits it once`() = runBlocking {
        dsl.execute("CREATE TRIGGER reject_translation BEFORE INSERT ON user_library WHEN NEW.book_md5 <> 'source' BEGIN SELECT RAISE(ABORT, 'secret publication failure'); END")
        val service = service(this)
        val id = service.start(owner, entryId, true).jobId
        awaitStopped(service, id)
        val audit = ActivityLogRepository(dsl)
        assertEquals(listOf("TRANSLATION_STARTED"), audit.findByUserId(owner, 1, 100).items.map { it.actionType })
        dsl.execute("DROP TRIGGER reject_translation")
        service.resume(owner, id)
        awaitStopped(service, id)
        assertEquals(1, audit.findByUserId(owner, 1, 100, "TRANSLATION_COMPLETED").items.size)
        assertEquals(1, audit.findByUserId(owner, 1, 100, "TRANSLATION_STARTED").items.size)
    }

    @Test fun `preflight requires consent ownership English and currently free model`() = runBlocking {
        val service = service(this)
        assertThrows(ValidationException::class.java) { runBlocking { service.start(owner, entryId, false) } }
        assertThrows(NotFoundException::class.java) { runBlocking { service.estimate(owner + 1, entryId) } }
        dsl.update(BOOKS).set(BOOKS.LANGUAGE, "pl").where(BOOKS.MD5.eq("source")).execute()
        assertThrows(ValidationException::class.java) { runBlocking { service.start(owner, entryId, true) } }
        dsl.update(BOOKS).set(BOOKS.LANGUAGE, "en").where(BOOKS.MD5.eq("source")).execute()
        coEvery { client.listFreeTextModels() } returns emptyList()
        assertThrows(ValidationException::class.java) { runBlocking { service.estimate(owner, entryId) } }
        Unit
    }

    @Test fun `duplicate job conflicts and cancellation cleans private workspace`() = runBlocking {
        val service = service(this)
        val id = service.start(owner, entryId, true).jobId
        try { service.start(owner, entryId, true); fail<Unit>("Duplicate job was accepted") } catch (_: ConflictException) { }
        assertThrows(NotFoundException::class.java) { service.status(owner + 1, id) }
        service.cancel(owner, id)
        assertEquals("cancelled", service.status(owner, id).status)
        assertFalse(dir.resolve("jobs").resolve(id).toFile().exists())
        assertTrue(dir.resolve("source.epub").toFile().exists())
    }

    @Test fun `failed library insert rolls back book and pauses recoverable publication`() = runBlocking {
        dsl.execute("CREATE TRIGGER reject_translation BEFORE INSERT ON user_library WHEN NEW.book_md5 <> 'source' BEGIN SELECT RAISE(ABORT, 'publication rejected'); END")
        val service = service(this)
        val id = service.start(owner, entryId, true).jobId
        awaitStopped(service, id)
        assertEquals("paused", service.status(owner, id).status)
        assertEquals("publication_failed", service.status(owner, id).error)
        assertEquals(1, dsl.selectCount().from(BOOKS).fetchOne(0, Int::class.java))
        assertEquals(1, entries.countByUserId(owner))
        dsl.execute("DROP TRIGGER reject_translation")
        service.resume(owner, id)
        awaitStopped(service, id)
        assertEquals("completed", service.status(owner, id).status)
        assertEquals(listOf(1, 1), chapters.findByJobId(id).map { it.attempts })
    }

    @Test fun `malformed completions retain actual usage after all three attempts`() = runBlocking {
        coEvery { client.translate(any(), any()) } returns OpenRouterCompletion("invalid JSON", 7, 5)
        val service = service(this)
        val id = service.start(owner, entryId, true).jobId
        awaitStopped(service, id)
        assertEquals("paused", service.status(owner, id).status)
        assertEquals(21, service.status(owner, id).actualInputTokens)
        assertEquals(15, service.status(owner, id).actualOutputTokens)
        assertEquals(3, chapters.findByJobId(id).first().attempts)
        assertEquals(1, entries.countByUserId(owner))
    }

    @Test fun `model becoming paid pauses immediately without retries`() = runBlocking {
        coEvery { client.translate(any(), any()) } throws OpenRouterException("not free", code = "model_no_longer_free")
        val service = service(this)
        val id = service.start(owner, entryId, true).jobId
        awaitStopped(service, id)
        assertEquals("model_no_longer_free", service.status(owner, id).error)
        assertEquals(1, chapters.findByJobId(id).first().attempts)
        coEvery { client.listFreeTextModels() } returns emptyList()
        try { service.resume(owner, id); fail<Unit>("Paid model was resumed") } catch (_: ValidationException) { }
        assertEquals("paused", service.status(owner, id).status)
    }

    @Test fun `one worker serializes requests for different users`() = runBlocking {
        val other = UserRepository(dsl).create("other@test.com", "hash", "Other", false, false).id!!
        val otherEntry = entries.add(other, "source", "epub").id!!
        entries.updateFilePath(other, "source", "epub", "source.epub")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var active = 0
        var maximumActive = 0
        coEvery { client.translate(any(), any()) } coAnswers {
            active++
            maximumActive = maxOf(maximumActive, active)
            entered.complete(Unit)
            release.await()
            active--
            OpenRouterCompletion(if (secondArg<String>().contains("Hello")) """["Cześć ","świecie","!"]""" else """["Drugi"]""")
        }
        val service = service(this)
        val first = service.start(owner, entryId, true).jobId
        entered.await()
        val second = service.start(other, otherEntry, true).jobId
        yield()
        assertEquals("queued", service.status(other, second).status)
        release.complete(Unit)
        awaitStopped(service, first)
        withTimeout(5000) { while (service.status(other, second).status in listOf("queued", "running")) yield() }
        assertEquals(1, maximumActive)
        assertEquals("completed", service.status(other, second).status)
    }

    @Test fun `restart requires explicit resume and skips completed chapters`() = runBlocking {
        coEvery { client.translate(any(), any()) } answers {
            if (!secondArg<String>().contains("Hello")) throw TimeoutException()
            OpenRouterCompletion("""["Cześć ","świecie","!"]""", 12, 9)
        }
        val firstService = service(this)
        val id = firstService.start(owner, entryId, true).jobId
        awaitStopped(firstService, id)
        jobs.markRunning(id)
        TranslationJobRepository(DatabaseFactory.create(dir.resolve("test.db").toString())).pauseInterruptedJobs()
        val restarted = service(this)
        assertEquals("paused", restarted.status(owner, id).status)
        assertEquals("server_restart", restarted.status(owner, id).error)
        coEvery { client.translate(any(), any()) } returns OpenRouterCompletion("""["Drugi"]""", 3, 2)
        restarted.resume(owner, id)
        awaitStopped(restarted, id)
        assertEquals("completed", restarted.status(owner, id).status)
        assertEquals(listOf(1, 4), chapters.findByJobId(id).map { it.attempts })
    }

    @Test fun `resume rejects modified source without losing workspace`() = runBlocking {
        coEvery { client.translate(any(), any()) } throws TimeoutException()
        val service = service(this)
        val id = service.start(owner, entryId, true).jobId
        awaitStopped(service, id)
        translationFixture(dir.resolve("source.epub"), "<p>Modified source</p>")
        try { service.resume(owner, id); fail<Unit>("Modified source was accepted") } catch (_: ValidationException) { }
        assertTrue(dir.resolve("jobs").resolve(id).toFile().exists())
        assertEquals("paused", service.status(owner, id).status)
    }
}
