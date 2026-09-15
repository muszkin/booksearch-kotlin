package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.TranslationChaptersRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.TRANSLATION_CHAPTERS
import java.time.Instant

class TranslationChapterRepository(private val dsl: DSLContext) {

    fun create(jobId: String, chapterIndex: Int, href: String) {
        val timestamp = now()
        dsl.insertInto(TRANSLATION_CHAPTERS)
            .set(TRANSLATION_CHAPTERS.JOB_ID, jobId)
            .set(TRANSLATION_CHAPTERS.CHAPTER_INDEX, chapterIndex)
            .set(TRANSLATION_CHAPTERS.HREF, href)
            .set(TRANSLATION_CHAPTERS.STATUS, STATUS_QUEUED)
            .set(TRANSLATION_CHAPTERS.CREATED_AT, timestamp)
            .set(TRANSLATION_CHAPTERS.UPDATED_AT, timestamp)
            .execute()
    }

    fun findByJobId(jobId: String): List<TranslationChaptersRecord> =
        dsl.selectFrom(TRANSLATION_CHAPTERS)
            .where(TRANSLATION_CHAPTERS.JOB_ID.eq(jobId))
            .orderBy(TRANSLATION_CHAPTERS.CHAPTER_INDEX.asc())
            .fetch()

    fun markAttemptStarted(jobId: String, chapterIndex: Int) {
        dsl.update(TRANSLATION_CHAPTERS)
            .set(TRANSLATION_CHAPTERS.STATUS, STATUS_RUNNING)
            .set(TRANSLATION_CHAPTERS.ATTEMPTS, TRANSLATION_CHAPTERS.ATTEMPTS.plus(1))
            .set(TRANSLATION_CHAPTERS.ERROR, null as String?)
            .set(TRANSLATION_CHAPTERS.UPDATED_AT, now())
            .where(TRANSLATION_CHAPTERS.JOB_ID.eq(jobId))
            .and(TRANSLATION_CHAPTERS.CHAPTER_INDEX.eq(chapterIndex))
            .execute()
    }

    fun markCompleted(jobId: String, chapterIndex: Int, inputTokens: Int, outputTokens: Int) {
        val timestamp = now()
        dsl.update(TRANSLATION_CHAPTERS)
            .set(TRANSLATION_CHAPTERS.STATUS, STATUS_COMPLETED)
            .set(TRANSLATION_CHAPTERS.INPUT_TOKENS, inputTokens)
            .set(TRANSLATION_CHAPTERS.OUTPUT_TOKENS, outputTokens)
            .set(TRANSLATION_CHAPTERS.ERROR, null as String?)
            .set(TRANSLATION_CHAPTERS.UPDATED_AT, timestamp)
            .set(TRANSLATION_CHAPTERS.COMPLETED_AT, timestamp)
            .where(TRANSLATION_CHAPTERS.JOB_ID.eq(jobId))
            .and(TRANSLATION_CHAPTERS.CHAPTER_INDEX.eq(chapterIndex))
            .execute()
    }

    fun markFailed(jobId: String, chapterIndex: Int, error: String) {
        dsl.update(TRANSLATION_CHAPTERS)
            .set(TRANSLATION_CHAPTERS.STATUS, STATUS_FAILED)
            .set(TRANSLATION_CHAPTERS.ERROR, error)
            .set(TRANSLATION_CHAPTERS.UPDATED_AT, now())
            .where(TRANSLATION_CHAPTERS.JOB_ID.eq(jobId))
            .and(TRANSLATION_CHAPTERS.CHAPTER_INDEX.eq(chapterIndex))
            .execute()
    }

    private fun now(): String = Instant.now().toString()

    companion object {
        const val STATUS_QUEUED = "queued"
        const val STATUS_RUNNING = "running"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}
