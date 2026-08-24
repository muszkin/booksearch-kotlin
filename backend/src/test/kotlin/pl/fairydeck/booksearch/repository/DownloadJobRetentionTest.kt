package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.jooq.generated.tables.references.DOWNLOAD_JOBS
import java.time.Instant
import java.time.temporal.ChronoUnit

class DownloadJobRetentionTest {

    private lateinit var dsl: DSLContext
    private lateinit var downloadJobRepository: DownloadJobRepository
    private var userId: Int = 0

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        downloadJobRepository = DownloadJobRepository(dsl)
        userId = UserRepository(dsl).create(
            email = "downloader@example.com",
            passwordHash = "hash",
            displayName = "downloader",
            isSuperAdmin = false,
            forcePasswordChange = false
        ).id!!
        BookRepository(dsl).upsertFromSearch(listOf(book()))
    }

    @Test
    fun sweepDeletesOldTerminalJobs() {
        val oldCompleted = createBackdatedJob(days = 60) { downloadJobRepository.markCompleted(it, "/library/book.epub") }
        val recentCompleted = createJob().also { downloadJobRepository.markCompleted(it, "/library/book.epub") }

        val deleted = downloadJobRepository.deleteTerminalOlderThan(Instant.now().minus(30, ChronoUnit.DAYS))

        assertEquals(1, deleted)
        assertNull(downloadJobRepository.findByIdAndUserId(oldCompleted, userId))
        assertNotNull(downloadJobRepository.findByIdAndUserId(recentCompleted, userId))
    }

    @Test
    fun oldActiveJobSurvivesTheSweepSoItCanStillBeResumed() {
        val oldQueued = createBackdatedJob(days = 60) { }

        downloadJobRepository.deleteTerminalOlderThan(Instant.now().minus(30, ChronoUnit.DAYS))

        val survivor = downloadJobRepository.findByIdAndUserId(oldQueued, userId)
        assertNotNull(survivor)
        assertEquals("queued", survivor!!.status)
    }

    @Test
    fun oldFailedAndCancelledJobsAreAlsoSwept() {
        val oldFailed = createBackdatedJob(days = 60) { downloadJobRepository.markFailed(it, "boom") }
        val oldCancelled = createBackdatedJob(days = 60) { downloadJobRepository.cancelJob(it, userId) }

        val deleted = downloadJobRepository.deleteTerminalOlderThan(Instant.now().minus(30, ChronoUnit.DAYS))

        assertEquals(2, deleted)
        assertNull(downloadJobRepository.findByIdAndUserId(oldFailed, userId))
        assertNull(downloadJobRepository.findByIdAndUserId(oldCancelled, userId))
    }

    private fun createJob(): Int = downloadJobRepository.create(userId, BOOK_MD5, "epub")

    private fun createBackdatedJob(days: Long, transition: (Int) -> Unit): Int {
        val jobId = createJob()
        transition(jobId)
        dsl.update(DOWNLOAD_JOBS)
            .set(DOWNLOAD_JOBS.CREATED_AT, Instant.now().minus(days, ChronoUnit.DAYS).toString())
            .where(DOWNLOAD_JOBS.ID.eq(jobId))
            .execute()
        return jobId
    }

    private fun book() = ParsedBookEntry(
        md5 = BOOK_MD5,
        title = "Solaris",
        author = "Stanisław Lem",
        language = "pl",
        format = "epub",
        fileSize = "1.2MB",
        detailUrl = "/md5/$BOOK_MD5",
        coverUrl = "",
        publisher = "WL",
        year = "1961",
        description = ""
    )

    private companion object {
        const val BOOK_MD5 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
    }
}
