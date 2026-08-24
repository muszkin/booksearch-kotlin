package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.SearchJobsRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.SEARCH_JOBS
import java.time.Instant

class SearchJobRepository(private val dsl: DSLContext) {

    fun create(userId: Int, query: String, language: String, format: String, maxPages: Int): Int {
        val now = Instant.now().toString()
        return dsl.insertInto(SEARCH_JOBS)
            .set(SEARCH_JOBS.USER_ID, userId)
            .set(SEARCH_JOBS.QUERY, query)
            .set(SEARCH_JOBS.LANGUAGE, language)
            .set(SEARCH_JOBS.FORMAT, format)
            .set(SEARCH_JOBS.MAX_PAGES, maxPages)
            .set(SEARCH_JOBS.STATUS, "queued")
            .set(SEARCH_JOBS.CREATED_AT, now)
            .set(SEARCH_JOBS.UPDATED_AT, now)
            .returningResult(SEARCH_JOBS.ID)
            .fetchOne()!!
            .get(SEARCH_JOBS.ID)!!
    }

    fun findByIdAndUserId(jobId: Int, userId: Int): SearchJobsRecord? =
        dsl.selectFrom(SEARCH_JOBS)
            .where(SEARCH_JOBS.ID.eq(jobId))
            .and(SEARCH_JOBS.USER_ID.eq(userId))
            .fetchOne()

    fun markScraping(jobId: Int) {
        dsl.update(SEARCH_JOBS)
            .set(SEARCH_JOBS.STATUS, "scraping")
            .set(SEARCH_JOBS.UPDATED_AT, Instant.now().toString())
            .where(SEARCH_JOBS.ID.eq(jobId))
            .execute()
    }

    fun markCompleted(jobId: Int, results: String, totalResults: Int) {
        dsl.update(SEARCH_JOBS)
            .set(SEARCH_JOBS.STATUS, "completed")
            .set(SEARCH_JOBS.RESULTS, results)
            .set(SEARCH_JOBS.TOTAL_RESULTS, totalResults)
            .set(SEARCH_JOBS.UPDATED_AT, Instant.now().toString())
            .where(SEARCH_JOBS.ID.eq(jobId))
            .execute()
    }

    fun deleteOlderThan(cutoff: Instant): Int =
        dsl.deleteFrom(SEARCH_JOBS)
            .where(SEARCH_JOBS.CREATED_AT.lt(cutoff.toString()))
            .execute()

    fun failNonTerminal(reason: String): Int =
        dsl.update(SEARCH_JOBS)
            .set(SEARCH_JOBS.STATUS, "failed")
            .set(SEARCH_JOBS.ERROR, reason)
            .set(SEARCH_JOBS.UPDATED_AT, Instant.now().toString())
            .where(SEARCH_JOBS.STATUS.`in`(NON_TERMINAL_STATUSES))
            .execute()

    fun markFailed(jobId: Int, error: String) {
        dsl.update(SEARCH_JOBS)
            .set(SEARCH_JOBS.STATUS, "failed")
            .set(SEARCH_JOBS.ERROR, error)
            .set(SEARCH_JOBS.UPDATED_AT, Instant.now().toString())
            .where(SEARCH_JOBS.ID.eq(jobId))
            .execute()
    }

    companion object {
        val NON_TERMINAL_STATUSES = listOf("queued", "scraping")
    }
}
