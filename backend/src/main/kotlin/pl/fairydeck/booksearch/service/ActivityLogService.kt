package pl.fairydeck.booksearch.service

import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.repository.ActivityLogRepository

class ActivityLogService(
    private val activityLogRepository: ActivityLogRepository
) {

    private val logger = LoggerFactory.getLogger(ActivityLogService::class.java)

    fun log(
        userId: Int,
        actionType: String,
        entityType: String,
        entityId: String? = null,
        details: String? = null,
        transactionDsl: DSLContext? = null
    ) {
        val repository = transactionDsl
            ?.let(::ActivityLogRepository)
            ?: activityLogRepository
        log(repository, userId, actionType, entityType, entityId, details)
    }

    private fun log(
        repository: ActivityLogRepository,
        userId: Int,
        actionType: String,
        entityType: String,
        entityId: String?,
        details: String?
    ) {
        try {
            repository.insert(userId, actionType, entityType, entityId, details)
        } catch (e: Exception) {
            logger.error("Failed to log activity: action={}, entity={}, entityId={}", actionType, entityType, entityId, e)
        }
    }

    fun logDual(
        adminUserId: Int,
        targetUserId: Int,
        actionType: String,
        entityType: String,
        entityId: String? = null,
        adminDetails: String? = null,
        targetDetails: String? = null,
        transactionDsl: DSLContext? = null
    ) {
        val repository = transactionDsl
            ?.let(::ActivityLogRepository)
            ?: activityLogRepository
        log(repository, adminUserId, actionType, entityType, entityId, adminDetails)
        log(repository, targetUserId, actionType, entityType, entityId, targetDetails)
    }
}
