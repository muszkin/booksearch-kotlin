package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.references.ACTIVITY_LOGS
import java.time.Instant

data class ActivityLogEntry(
    val id: Int,
    val userId: Int,
    val actionType: String,
    val entityType: String,
    val entityId: String?,
    val details: String?,
    val createdAt: String
)

class ActivityLogRepository(private val dsl: DSLContext) {

    fun insert(userId: Int, actionType: String, entityType: String, entityId: String?, details: String?) {
        dsl.insertInto(ACTIVITY_LOGS)
            .set(ACTIVITY_LOGS.USER_ID, userId)
            .set(ACTIVITY_LOGS.ACTION_TYPE, actionType)
            .set(ACTIVITY_LOGS.ENTITY_TYPE, entityType)
            .set(ACTIVITY_LOGS.ENTITY_ID, entityId)
            .set(ACTIVITY_LOGS.DETAILS, details)
            .set(ACTIVITY_LOGS.CREATED_AT, Instant.now().toString())
            .execute()
    }

    fun findByUserId(
        userId: Int,
        page: Int,
        pageSize: Int,
        actionType: String? = null
    ): PaginatedResult<ActivityLogEntry> {
        val baseCondition = ACTIVITY_LOGS.USER_ID.eq(userId)
        val condition = if (actionType != null) {
            baseCondition.and(ACTIVITY_LOGS.ACTION_TYPE.eq(actionType))
        } else {
            baseCondition
        }

        val totalCount = dsl.selectCount()
            .from(ACTIVITY_LOGS)
            .where(condition)
            .fetchOne(0, Long::class.java) ?: 0L

        val offset = (page - 1) * pageSize
        val items = dsl.selectFrom(ACTIVITY_LOGS)
            .where(condition)
            .orderBy(ACTIVITY_LOGS.CREATED_AT.desc())
            .limit(pageSize)
            .offset(offset)
            .fetch { record ->
                ActivityLogEntry(
                    id = record.id!!,
                    userId = record.userId!!,
                    actionType = record.actionType!!,
                    entityType = record.entityType!!,
                    entityId = record.entityId,
                    details = record.details,
                    createdAt = record.createdAt!!
                )
            }

        return PaginatedResult(items = items, totalCount = totalCount)
    }
}
