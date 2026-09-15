package pl.fairydeck.booksearch.service

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.ParsedBookEntry
import pl.fairydeck.booksearch.infrastructure.RetentionConfig
import pl.fairydeck.booksearch.jooq.generated.tables.references.DOWNLOAD_JOBS
import pl.fairydeck.booksearch.jooq.generated.tables.references.SEARCH_JOBS
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.repository.SearchJobRepository
import pl.fairydeck.booksearch.repository.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class JobRetentionServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var searchJobRepository: SearchJobRepository
    private lateinit var downloadJobRepository: DownloadJobRepository
    private lateinit var jobRetentionService: JobRetentionService
    private var userId: Int = 0

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        searchJobRepository = SearchJobRepository(dsl)
        downloadJobRepository = DownloadJobRepository(dsl)
        jobRetentionService = JobRetentionService(
            searchJobRepository,
            downloadJobRepository,
            RetentionConfig(searchJobDays = 1, downloadJobDays = 30)
        )
        userId = UserRepository(dsl).create(
            email = "keeper@example.com",
            passwordHash = "hash",
            displayName = "keeper",
            isSuperAdmin = false,
            forcePasswordChange = false
        ).id!!
        BookRepository(dsl).upsertFromSearch(listOf(book()))
    }

    @Test
    fun sweepAppliesSeparateWindowsToEachTable() {
        val staleSearch = searchJobRepository.create(userId, "stale", "pl", "epub", 3)
        backdateSearchJob(staleSearch, days = 2)
        val youngSearch = searchJobRepository.create(userId, "young", "pl", "epub", 3)

        val staleDownload = downloadJobRepository.create(userId, BOOK_MD5, "epub")
        downloadJobRepository.markCompleted(staleDownload, "/library/book.epub")
        backdateDownloadJob(staleDownload, days = 40)

        val youngDownload = downloadJobRepository.create(userId, BOOK_MD5, "mobi")
        downloadJobRepository.markCompleted(youngDownload, "/library/book.mobi")
        backdateDownloadJob(youngDownload, days = 2)

        jobRetentionService.sweep()

        assertNull(searchJobRepository.findByIdAndUserId(staleSearch, userId))
        assertNotNull(searchJobRepository.findByIdAndUserId(youngSearch, userId))
        assertNull(downloadJobRepository.findByIdAndUserId(staleDownload, userId))
        assertNotNull(downloadJobRepository.findByIdAndUserId(youngDownload, userId))
    }

    @Test
    fun sweepKeepsOldDownloadJobsThatAreStillActive() {
        val activeDownload = downloadJobRepository.create(userId, BOOK_MD5, "epub")
        backdateDownloadJob(activeDownload, days = 40)

        jobRetentionService.sweep()

        assertNotNull(downloadJobRepository.findByIdAndUserId(activeDownload, userId))
    }

    @Test
    fun failInterruptedSearchJobsMarksNonTerminalJobsFailed() {
        val interrupted = searchJobRepository.create(userId, "interrupted", "pl", "epub", 3)

        jobRetentionService.failInterruptedSearchJobs()

        val job = searchJobRepository.findByIdAndUserId(interrupted, userId)!!
        assertEquals("failed", job.status)
        assertNotNull(job.error)
    }

    private fun backdateSearchJob(jobId: Int, days: Long) {
        dsl.update(SEARCH_JOBS)
            .set(SEARCH_JOBS.CREATED_AT, Instant.now().minus(days, ChronoUnit.DAYS).toString())
            .where(SEARCH_JOBS.ID.eq(jobId))
            .execute()
    }

    private fun backdateDownloadJob(jobId: Int, days: Long) {
        dsl.update(DOWNLOAD_JOBS)
            .set(DOWNLOAD_JOBS.CREATED_AT, Instant.now().minus(days, ChronoUnit.DAYS).toString())
            .where(DOWNLOAD_JOBS.ID.eq(jobId))
            .execute()
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
