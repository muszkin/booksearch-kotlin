package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.jooq.generated.tables.references.SEARCH_JOBS
import java.time.Instant
import java.time.temporal.ChronoUnit

class SearchJobRepositoryTest {

    private lateinit var dsl: DSLContext
    private lateinit var searchJobRepository: SearchJobRepository
    private var userId: Int = 0

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        searchJobRepository = SearchJobRepository(dsl)
        userId = createUser("seeker@example.com")
    }

    private fun createUser(email: String): Int =
        UserRepository(dsl).create(
            email = email,
            passwordHash = "hash",
            displayName = email.substringBefore('@'),
            isSuperAdmin = false,
            forcePasswordChange = false
        ).id!!

    @Test
    fun createdJobStartsQueued() {
        val jobId = searchJobRepository.create(userId, "lem", "pl", "epub", maxPages = 3)

        val job = searchJobRepository.findByIdAndUserId(jobId, userId)

        assertNotNull(job)
        assertEquals("queued", job!!.status)
        assertEquals("lem", job.query)
        assertNull(job.results)
    }

    @Test
    fun completedJobStoresSerializedResults() {
        val jobId = searchJobRepository.create(userId, "lem", "pl", "epub", maxPages = 3)

        searchJobRepository.markCompleted(jobId, """[{"md5":"abc"}]""", totalResults = 1)

        val job = searchJobRepository.findByIdAndUserId(jobId, userId)!!
        assertEquals("completed", job.status)
        assertEquals("""[{"md5":"abc"}]""", job.results)
        assertEquals(1, job.totalResults)
    }

    @Test
    fun failedJobStoresErrorMessage() {
        val jobId = searchJobRepository.create(userId, "lem", "pl", "epub", maxPages = 3)

        searchJobRepository.markFailed(jobId, "No working mirror available")

        val job = searchJobRepository.findByIdAndUserId(jobId, userId)!!
        assertEquals("failed", job.status)
        assertEquals("No working mirror available", job.error)
    }

    @Test
    fun jobIsNotVisibleToAnotherUser() {
        val otherUserId = createUser("intruder@example.com")
        val jobId = searchJobRepository.create(userId, "lem", "pl", "epub", maxPages = 3)

        assertNull(searchJobRepository.findByIdAndUserId(jobId, otherUserId))
    }

    @Test
    fun sweepDeletesJobsOlderThanTheCutoff() {
        val staleId = searchJobRepository.create(userId, "stale", "pl", "epub", maxPages = 3)
        val freshId = searchJobRepository.create(userId, "fresh", "pl", "epub", maxPages = 3)
        backdate(staleId, days = 3)

        val deleted = searchJobRepository.deleteOlderThan(Instant.now().minus(1, ChronoUnit.DAYS))

        assertEquals(1, deleted)
        assertNull(searchJobRepository.findByIdAndUserId(staleId, userId))
        assertNotNull(searchJobRepository.findByIdAndUserId(freshId, userId))
    }

    @Test
    fun nonTerminalJobsAreFailedSoPollingStops() {
        val queuedId = searchJobRepository.create(userId, "queued", "pl", "epub", maxPages = 3)
        val scrapingId = searchJobRepository.create(userId, "scraping", "pl", "epub", maxPages = 3)
        searchJobRepository.markScraping(scrapingId)
        val completedId = searchJobRepository.create(userId, "done", "pl", "epub", maxPages = 3)
        searchJobRepository.markCompleted(completedId, "[]", totalResults = 0)

        val failed = searchJobRepository.failNonTerminal("Interrupted by a restart")

        assertEquals(2, failed)
        assertEquals("failed", searchJobRepository.findByIdAndUserId(queuedId, userId)!!.status)
        assertEquals("failed", searchJobRepository.findByIdAndUserId(scrapingId, userId)!!.status)
        assertEquals("completed", searchJobRepository.findByIdAndUserId(completedId, userId)!!.status)
    }

    private fun backdate(jobId: Int, days: Long) {
        dsl.update(SEARCH_JOBS)
            .set(SEARCH_JOBS.CREATED_AT, Instant.now().minus(days, ChronoUnit.DAYS).toString())
            .where(SEARCH_JOBS.ID.eq(jobId))
            .execute()
    }
}
