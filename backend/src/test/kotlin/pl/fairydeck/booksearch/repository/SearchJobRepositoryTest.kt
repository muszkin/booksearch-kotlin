package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory

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
}
