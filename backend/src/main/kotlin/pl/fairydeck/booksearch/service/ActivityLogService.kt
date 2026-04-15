package pl.fairydeck.booksearch.service

import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.repository.ActivityLogRepository

class ActivityLogService(
    private val activityLogRepository: ActivityLogRepository
) {

    private val logger = LoggerFactory.getLogger(ActivityLogService::class.java)

    fun log(userId: Int, actionType: String, entityType: String, entityId: String? = null, details: String? = null) {
        try {
            activityLogRepository.insert(userId, actionType, entityType, entityId, details)
        } catch (e: Exception) {
            logger.error("Failed to log activity: action={}, entity={}, entityId={}", actionType, entityType, entityId, e)
        }
    }
}
