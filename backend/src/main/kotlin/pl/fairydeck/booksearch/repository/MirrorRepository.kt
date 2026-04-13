package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.MirrorsRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.MIRRORS
import java.time.Instant

class MirrorRepository(private val dsl: DSLContext) {

    fun upsert(domain: String, baseUrl: String, isWorking: Boolean, responseTimeMs: Int) {
        val now = Instant.now().toString()
        val workingFlag = if (isWorking) 1 else 0
        dsl.insertInto(MIRRORS)
            .set(MIRRORS.DOMAIN, domain)
            .set(MIRRORS.BASE_URL, baseUrl)
            .set(MIRRORS.IS_WORKING, workingFlag)
            .set(MIRRORS.RESPONSE_TIME_MS, responseTimeMs)
            .set(MIRRORS.LAST_CHECKED_AT, now)
            .onConflict(MIRRORS.DOMAIN)
            .doUpdate()
            .set(MIRRORS.BASE_URL, baseUrl)
            .set(MIRRORS.IS_WORKING, workingFlag)
            .set(MIRRORS.RESPONSE_TIME_MS, responseTimeMs)
            .set(MIRRORS.LAST_CHECKED_AT, now)
            .execute()
    }

    fun findBestWorking(): MirrorsRecord? =
        dsl.selectFrom(MIRRORS)
            .where(MIRRORS.IS_WORKING.eq(1))
            .orderBy(MIRRORS.RESPONSE_TIME_MS.asc())
            .limit(1)
            .fetchOne()

    fun findAll(): List<MirrorsRecord> =
        dsl.selectFrom(MIRRORS)
            .orderBy(MIRRORS.RESPONSE_TIME_MS.asc().nullsLast())
            .fetch()

    fun updateStatus(domain: String, isWorking: Boolean, responseTimeMs: Int) {
        val now = Instant.now().toString()
        dsl.update(MIRRORS)
            .set(MIRRORS.IS_WORKING, if (isWorking) 1 else 0)
            .set(MIRRORS.RESPONSE_TIME_MS, responseTimeMs)
            .set(MIRRORS.LAST_CHECKED_AT, now)
            .where(MIRRORS.DOMAIN.eq(domain))
            .execute()
    }
}
