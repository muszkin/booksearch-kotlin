package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.repository.ActivityLogRepository
import pl.fairydeck.booksearch.repository.RequestLogRepository

fun Route.logRoutes(
    activityLogRepository: ActivityLogRepository,
    requestLogRepository: RequestLogRepository
) {
    authenticate("jwt") {
        route("/api/logs") {
            get("/activity") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val type = call.request.queryParameters["type"]

                val result = activityLogRepository.findByUserId(principal.userId, page, pageSize, type)
                val items = result.items.map { entry ->
                    ActivityLogItem(
                        id = entry.id,
                        actionType = entry.actionType,
                        entityType = entry.entityType,
                        entityId = entry.entityId,
                        details = entry.details,
                        createdAt = entry.createdAt
                    )
                }
                call.respond(HttpStatusCode.OK, ActivityLogListResponse(items = items, totalCount = result.totalCount))
            }

            get("/requests") {
                requireSuperAdmin(call)

                val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
                val method = call.request.queryParameters["method"]
                val path = call.request.queryParameters["path"]
                val statusCode = call.request.queryParameters["statusCode"]?.toIntOrNull()
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]

                val result = requestLogRepository.findAll(page, pageSize, method, path, statusCode, from, to)
                val items = result.items.map { entry ->
                    RequestLogItem(
                        id = entry.id,
                        method = entry.method,
                        path = entry.path,
                        statusCode = entry.statusCode,
                        durationMs = entry.durationMs,
                        requestId = entry.requestId,
                        userId = entry.userId,
                        createdAt = entry.createdAt
                    )
                }
                call.respond(HttpStatusCode.OK, RequestLogListResponse(items = items, totalCount = result.totalCount))
            }
        }
    }
}

@Serializable
data class ActivityLogItem(
    val id: Int,
    val actionType: String,
    val entityType: String,
    val entityId: String?,
    val details: String?,
    val createdAt: String
)

@Serializable
data class ActivityLogListResponse(
    val items: List<ActivityLogItem>,
    val totalCount: Long
)

@Serializable
data class RequestLogItem(
    val id: Int,
    val method: String,
    val path: String,
    val statusCode: Int,
    val durationMs: Int,
    val requestId: String?,
    val userId: Int?,
    val createdAt: String
)

@Serializable
data class RequestLogListResponse(
    val items: List<RequestLogItem>,
    val totalCount: Long
)
