package pl.fairydeck.booksearch.repository

import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import pl.fairydeck.booksearch.jooq.generated.tables.references.REQUEST_LOGS
import java.time.Instant

data class RequestLogEntry(
    val id: Int,
    val method: String,
    val path: String,
    val statusCode: Int,
    val durationMs: Int,
    val requestHeaders: String?,
    val responseHeaders: String?,
    val requestId: String?,
    val userId: Int?,
    val createdAt: String
)

class RequestLogRepository(private val dsl: DSLContext) {

    fun insert(
        method: String,
        path: String,
        statusCode: Int,
        durationMs: Int,
        requestHeaders: String?,
        responseHeaders: String?,
        requestId: String?,
        userId: Int?
    ) {
        dsl.insertInto(REQUEST_LOGS)
            .set(REQUEST_LOGS.METHOD, method)
            .set(REQUEST_LOGS.PATH, path)
            .set(REQUEST_LOGS.STATUS_CODE, statusCode)
            .set(REQUEST_LOGS.DURATION_MS, durationMs)
            .set(REQUEST_LOGS.REQUEST_HEADERS, requestHeaders)
            .set(REQUEST_LOGS.RESPONSE_HEADERS, responseHeaders)
            .set(REQUEST_LOGS.REQUEST_ID, requestId)
            .set(REQUEST_LOGS.USER_ID, userId)
            .set(REQUEST_LOGS.CREATED_AT, Instant.now().toString())
            .execute()
    }

    fun findAll(
        page: Int,
        pageSize: Int,
        method: String? = null,
        path: String? = null,
        statusCode: Int? = null,
        from: String? = null,
        to: String? = null
    ): PaginatedResult<RequestLogEntry> {
        var condition: Condition = DSL.trueCondition()

        if (method != null) {
            condition = condition.and(REQUEST_LOGS.METHOD.eq(method))
        }
        if (path != null) {
            condition = condition.and(REQUEST_LOGS.PATH.eq(path))
        }
        if (statusCode != null) {
            condition = condition.and(REQUEST_LOGS.STATUS_CODE.eq(statusCode))
        }
        if (from != null) {
            condition = condition.and(REQUEST_LOGS.CREATED_AT.ge(from))
        }
        if (to != null) {
            condition = condition.and(REQUEST_LOGS.CREATED_AT.le(to))
        }

        val totalCount = dsl.selectCount()
            .from(REQUEST_LOGS)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L

        val offset = (page - 1) * pageSize
        val items = dsl.selectFrom(REQUEST_LOGS)
            .where(condition)
            .orderBy(REQUEST_LOGS.CREATED_AT.desc())
            .limit(pageSize)
            .offset(offset)
            .fetch { record ->
                RequestLogEntry(
                    id = record.id!!,
                    method = record.method!!,
                    path = record.path!!,
                    statusCode = record.statusCode!!,
                    durationMs = record.durationMs!!,
                    requestHeaders = record.requestHeaders,
                    responseHeaders = record.responseHeaders,
                    requestId = record.requestId,
                    userId = record.userId,
                    createdAt = record.createdAt!!
                )
            }

        return PaginatedResult(items = items, totalCount = totalCount)
    }
}
