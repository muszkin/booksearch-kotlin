package pl.fairydeck.booksearch.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import java.time.Instant
import java.util.UUID

class TranslationJobRepositoryTest {

    private lateinit var users: UserRepository
    private lateinit var library: UserLibraryRepository
    private lateinit var jobs: TranslationJobRepository
    private lateinit var chapters: TranslationChapterRepository
    private lateinit var config: SystemConfigRepository
    private var ownerId = 0
    private var otherUserId = 0
    private var libraryEntryId = 0
    private var outputLibraryEntryId = 0

    @BeforeEach
    fun setUp() {
        val dsl = DatabaseFactory.createInMemory()
        users = UserRepository(dsl)
        library = UserLibraryRepository(dsl)
        jobs = TranslationJobRepository(dsl)
        chapters = TranslationChapterRepository(dsl)
        config = SystemConfigRepository(dsl)

        ownerId = users.create("owner@example.test", "hash", "Owner", false, false).id!!
        otherUserId = users.create("other@example.test", "hash", "Other", false, false).id!!
        dsl.insertInto(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS)
            .set(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS.MD5, "source-md5")
            .set(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS.TITLE, "Source")
            .set(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS.AUTHOR, "Author")
            .set(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS.INDEXED_AT, Instant.now().toString())
            .execute()
        libraryEntryId = library.add(ownerId, "source-md5", "epub").id!!
        dsl.insertInto(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS)
            .set(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS.MD5, "output-md5")
            .set(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS.TITLE, "Output")
            .set(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS.AUTHOR, "Author")
            .set(pl.fairydeck.booksearch.jooq.generated.tables.references.BOOKS.INDEXED_AT, Instant.now().toString())
            .execute()
        outputLibraryEntryId = library.add(ownerId, "output-md5", "epub").id!!
    }

    @Test
    fun `job is invisible to another user`() {
        val id = jobs.create(ownerId, libraryEntryId, "free-model")

        assertNotNull(UUID.fromString(id))
        assertNull(jobs.findByIdAndUserId(id, otherUserId))
        assertEquals("queued", jobs.findByIdAndUserId(id, ownerId)!!.status)
    }

    @Test
    fun `startup pauses queued and running jobs with restart reason`() {
        val queuedId = jobs.create(ownerId, libraryEntryId, "free-model")
        val runningId = jobs.create(ownerId, libraryEntryId, "free-model")
        jobs.markRunning(runningId)

        jobs.pauseInterruptedJobs()

        assertEquals("paused", jobs.findByIdAndUserId(queuedId, ownerId)!!.status)
        assertEquals("server_restart", jobs.findByIdAndUserId(queuedId, ownerId)!!.error)
        assertEquals("paused", jobs.findByIdAndUserId(runningId, ownerId)!!.status)
        assertEquals("server_restart", jobs.findByIdAndUserId(runningId, ownerId)!!.error)
    }

    @Test
    fun `chapter attempts and token progress are persisted without prose`() {
        val jobId = jobs.create(ownerId, libraryEntryId, "free-model")
        chapters.create(jobId, 0, "text/chapter-1.xhtml")

        chapters.markAttemptStarted(jobId, 0)
        chapters.markCompleted(jobId, 0, inputTokens = 123, outputTokens = 456)

        val chapter = chapters.findByJobId(jobId).single()
        assertEquals("completed", chapter.status)
        assertEquals(1, chapter.attempts)
        assertEquals(123, chapter.inputTokens)
        assertEquals(456, chapter.outputTokens)
    }

    @Test
    fun `job persists workspace output error and aggregate progress`() {
        val jobId = jobs.create(ownerId, libraryEntryId, "free-model")

        jobs.updatePlan(jobId, "source-md5", "/library/source.epub", "/work/$jobId", 3, 1000)
        jobs.updateProgress(jobId, completedChapters = 1, failedChapterIndex = 2, inputTokens = 123, outputTokens = 456)
        jobs.markPaused(jobId, "chapter_failed")
        val paused = jobs.findByIdAndUserId(jobId, ownerId)!!
        assertEquals("chapter_failed", paused.error)
        assertEquals(2, paused.failedChapterIndex)
        jobs.markCompleted(jobId, outputLibraryEntryId)

        val job = jobs.findByIdAndUserId(jobId, ownerId)!!
        assertEquals("completed", job.status)
        assertEquals("source-md5", job.sourceBookMd5)
        assertEquals("/library/source.epub", job.sourceFilePath)
        assertEquals("/work/$jobId", job.workspacePath)
        assertEquals(3, job.totalChapters)
        assertEquals(1, job.completedChapters)
        assertNull(job.failedChapterIndex)
        assertEquals(1000, job.estimatedInputTokens)
        assertEquals(123, job.actualInputTokens)
        assertEquals(456, job.actualOutputTokens)
        assertEquals(outputLibraryEntryId, job.outputLibraryEntryId)
        assertNotNull(job.completedAt)
    }

    @Test
    fun `translation default model is stored as system configuration`() {
        assertNull(config.getTranslationDefaultModel())

        config.setTranslationDefaultModel("free-model")

        assertEquals("free-model", config.getTranslationDefaultModel())
    }
}
