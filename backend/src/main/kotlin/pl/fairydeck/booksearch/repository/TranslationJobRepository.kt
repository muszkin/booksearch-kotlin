package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.TranslationJobsRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.TRANSLATION_JOBS
import java.time.Instant
import java.util.UUID

class TranslationJobRepository(private val dsl: DSLContext) {

    fun create(userId: Int, sourceLibraryEntryId: Int, modelId: String): String {
        val id = UUID.randomUUID().toString()
        val now = now()
        dsl.insertInto(TRANSLATION_JOBS)
            .set(TRANSLATION_JOBS.ID, id)
            .set(TRANSLATION_JOBS.USER_ID, userId)
            .set(TRANSLATION_JOBS.SOURCE_LIBRARY_ENTRY_ID, sourceLibraryEntryId)
            .set(TRANSLATION_JOBS.MODEL_ID, modelId)
            .set(TRANSLATION_JOBS.STATUS, STATUS_QUEUED)
            .set(TRANSLATION_JOBS.CREATED_AT, now)
            .set(TRANSLATION_JOBS.UPDATED_AT, now)
            .execute()
        return id
    }

    fun findByIdAndUserId(id: String, userId: Int): TranslationJobsRecord? =
        dsl.selectFrom(TRANSLATION_JOBS)
            .where(TRANSLATION_JOBS.ID.eq(id))
            .and(TRANSLATION_JOBS.USER_ID.eq(userId))
            .fetchOne()

    fun findActiveBySourceLibraryEntryId(userId: Int, sourceLibraryEntryId: Int): TranslationJobsRecord? =
        dsl.selectFrom(TRANSLATION_JOBS)
            .where(TRANSLATION_JOBS.USER_ID.eq(userId))
            .and(TRANSLATION_JOBS.SOURCE_LIBRARY_ENTRY_ID.eq(sourceLibraryEntryId))
            .and(TRANSLATION_JOBS.STATUS.`in`(STATUS_QUEUED, STATUS_RUNNING, STATUS_PAUSED))
            .fetchOne()

    fun findActiveByUserId(userId: Int): List<TranslationJobsRecord> =
        dsl.selectFrom(TRANSLATION_JOBS)
            .where(TRANSLATION_JOBS.USER_ID.eq(userId))
            .and(TRANSLATION_JOBS.STATUS.`in`(STATUS_QUEUED, STATUS_RUNNING, STATUS_PAUSED))
            .orderBy(TRANSLATION_JOBS.CREATED_AT.desc(), TRANSLATION_JOBS.ID.desc())
            .fetch()

    fun markRunning(id: String) {
        updateStatus(id, STATUS_RUNNING)
    }

    fun queue(id: String) {
        updateStatus(id, STATUS_QUEUED)
    }

    fun pauseInterruptedJobs(): Int =
        dsl.update(TRANSLATION_JOBS)
            .set(TRANSLATION_JOBS.STATUS, STATUS_PAUSED)
            .set(TRANSLATION_JOBS.ERROR, ERROR_SERVER_RESTART)
            .set(TRANSLATION_JOBS.UPDATED_AT, now())
            .where(TRANSLATION_JOBS.STATUS.`in`(STATUS_QUEUED, STATUS_RUNNING))
            .execute()

    fun updatePlan(
        id: String,
        sourceBookMd5: String,
        sourceFilePath: String,
        workspacePath: String,
        totalChapters: Int,
        estimatedInputTokens: Int
    ) {
        dsl.update(TRANSLATION_JOBS)
            .set(TRANSLATION_JOBS.SOURCE_BOOK_MD5, sourceBookMd5)
            .set(TRANSLATION_JOBS.SOURCE_FILE_PATH, sourceFilePath)
            .set(TRANSLATION_JOBS.WORKSPACE_PATH, workspacePath)
            .set(TRANSLATION_JOBS.TOTAL_CHAPTERS, totalChapters)
            .set(TRANSLATION_JOBS.ESTIMATED_INPUT_TOKENS, estimatedInputTokens)
            .set(TRANSLATION_JOBS.UPDATED_AT, now())
            .where(TRANSLATION_JOBS.ID.eq(id))
            .execute()
    }

    fun updateProgress(
        id: String,
        completedChapters: Int,
        failedChapterIndex: Int?,
        inputTokens: Int,
        outputTokens: Int
    ) {
        dsl.update(TRANSLATION_JOBS)
            .set(TRANSLATION_JOBS.COMPLETED_CHAPTERS, completedChapters)
            .set(TRANSLATION_JOBS.FAILED_CHAPTER_INDEX, failedChapterIndex)
            .set(TRANSLATION_JOBS.ACTUAL_INPUT_TOKENS, inputTokens)
            .set(TRANSLATION_JOBS.ACTUAL_OUTPUT_TOKENS, outputTokens)
            .set(TRANSLATION_JOBS.UPDATED_AT, now())
            .where(TRANSLATION_JOBS.ID.eq(id))
            .execute()
    }

    fun markPaused(id: String, error: String, failedChapterIndex: Int? = null) {
        val update = dsl.update(TRANSLATION_JOBS)
            .set(TRANSLATION_JOBS.STATUS, STATUS_PAUSED)
            .set(TRANSLATION_JOBS.ERROR, error)
            .set(TRANSLATION_JOBS.UPDATED_AT, now())

        if (failedChapterIndex != null) {
            update.set(TRANSLATION_JOBS.FAILED_CHAPTER_INDEX, failedChapterIndex)
        }

        update.where(TRANSLATION_JOBS.ID.eq(id)).execute()
    }

    fun markCompleted(id: String, outputLibraryEntryId: Int) {
        val timestamp = now()
        dsl.update(TRANSLATION_JOBS)
            .set(TRANSLATION_JOBS.STATUS, STATUS_COMPLETED)
            .set(TRANSLATION_JOBS.OUTPUT_LIBRARY_ENTRY_ID, outputLibraryEntryId)
            .set(TRANSLATION_JOBS.ERROR, null as String?)
            .set(TRANSLATION_JOBS.FAILED_CHAPTER_INDEX, null as Int?)
            .set(TRANSLATION_JOBS.UPDATED_AT, timestamp)
            .set(TRANSLATION_JOBS.COMPLETED_AT, timestamp)
            .where(TRANSLATION_JOBS.ID.eq(id))
            .execute()
    }

    fun markFailed(id: String, error: String, failedChapterIndex: Int? = null) {
        dsl.update(TRANSLATION_JOBS)
            .set(TRANSLATION_JOBS.STATUS, STATUS_FAILED)
            .set(TRANSLATION_JOBS.ERROR, error)
            .set(TRANSLATION_JOBS.FAILED_CHAPTER_INDEX, failedChapterIndex)
            .set(TRANSLATION_JOBS.UPDATED_AT, now())
            .where(TRANSLATION_JOBS.ID.eq(id))
            .execute()
    }

    fun cancel(id: String) {
        updateStatus(id, STATUS_CANCELLED)
    }

    private fun updateStatus(id: String, status: String) {
        dsl.update(TRANSLATION_JOBS)
            .set(TRANSLATION_JOBS.STATUS, status)
            .set(TRANSLATION_JOBS.UPDATED_AT, now())
            .where(TRANSLATION_JOBS.ID.eq(id))
            .execute()
    }

    private fun now(): String = Instant.now().toString()

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        const val STATUS_CANCELLED = "cancelled"
        const val ERROR_SERVER_RESTART = "server_restart"
    }
}
