package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.DownloadJobsRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.DOWNLOAD_JOBS
import java.time.Instant

class DownloadJobRepository(private val dsl: DSLContext) {

    fun create(userId: Int, bookMd5: String, format: String): Int {
        val now = Instant.now().toString()
        return dsl.insertInto(DOWNLOAD_JOBS)
            .set(DOWNLOAD_JOBS.USER_ID, userId)
            .set(DOWNLOAD_JOBS.BOOK_MD5, bookMd5)
            .set(DOWNLOAD_JOBS.FORMAT, format)
            .set(DOWNLOAD_JOBS.STATUS, "queued")
            .set(DOWNLOAD_JOBS.PROGRESS, 0)
            .set(DOWNLOAD_JOBS.CREATED_AT, now)
            .set(DOWNLOAD_JOBS.UPDATED_AT, now)
            .returningResult(DOWNLOAD_JOBS.ID)
            .fetchOne()!!
            .get(DOWNLOAD_JOBS.ID)!!
    }

    fun findByIdAndUserId(jobId: Int, userId: Int): DownloadJobsRecord? =
        dsl.selectFrom(DOWNLOAD_JOBS)
            .where(DOWNLOAD_JOBS.ID.eq(jobId))
            .and(DOWNLOAD_JOBS.USER_ID.eq(userId))
            .fetchOne()

    fun updateProgress(jobId: Int, status: String, progress: Int) {
        dsl.update(DOWNLOAD_JOBS)
            .set(DOWNLOAD_JOBS.STATUS, status)
            .set(DOWNLOAD_JOBS.PROGRESS, progress)
            .set(DOWNLOAD_JOBS.UPDATED_AT, Instant.now().toString())
            .where(DOWNLOAD_JOBS.ID.eq(jobId))
            .execute()
    }

    fun markCompleted(jobId: Int, filePath: String) {
        dsl.update(DOWNLOAD_JOBS)
            .set(DOWNLOAD_JOBS.STATUS, "completed")
            .set(DOWNLOAD_JOBS.PROGRESS, 100)
            .set(DOWNLOAD_JOBS.FILE_PATH, filePath)
            .set(DOWNLOAD_JOBS.UPDATED_AT, Instant.now().toString())
            .where(DOWNLOAD_JOBS.ID.eq(jobId))
            .execute()
    }

    fun markFailed(jobId: Int, error: String) {
        dsl.update(DOWNLOAD_JOBS)
            .set(DOWNLOAD_JOBS.STATUS, "failed")
            .set(DOWNLOAD_JOBS.ERROR, error)
            .set(DOWNLOAD_JOBS.UPDATED_AT, Instant.now().toString())
            .where(DOWNLOAD_JOBS.ID.eq(jobId))
            .execute()
    }
}
