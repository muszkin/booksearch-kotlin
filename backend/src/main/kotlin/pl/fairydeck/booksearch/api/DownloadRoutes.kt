package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.service.ActivityLogService
import pl.fairydeck.booksearch.service.DownloadService

fun Route.downloadRoutes(
    downloadService: DownloadService,
    downloadJobRepository: DownloadJobRepository,
    activityLogService: ActivityLogService
) {
    authenticate("jwt") {
        route("/api/download") {
            post("/{md5}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val md5 = call.parameters["md5"]
                    ?: throw ValidationException("Missing md5 parameter")

                val jobId = downloadService.startDownload(principal.userId, md5)
                activityLogService.log(principal.userId, "DOWNLOAD_STARTED", "download_job", jobId.toString())
                call.respond(HttpStatusCode.Accepted, DownloadStartedResponse(jobId = jobId, status = "queued"))
            }

            get("/status/{jobId}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val jobId = call.parameters["jobId"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid job ID")

                val status = downloadService.getJobStatus(jobId, principal.userId)
                    ?: throw NotFoundException("Download job not found")

                call.respond(HttpStatusCode.OK, DownloadStatusResponse(
                    jobId = status.id,
                    status = status.status,
                    progress = status.progress,
                    filePath = status.filePath,
                    error = status.error
                ))
            }

            get("/jobs") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val status = call.request.queryParameters["status"]
                val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20).coerceIn(1, 100)

                val result = downloadJobRepository.findAllByUserId(principal.userId, status, page, pageSize)
                val items = result.items.map { record ->
                    DownloadJobItem(
                        jobId = record.id!!,
                        bookMd5 = record.bookMd5!!,
                        format = record.format!!,
                        status = record.status!!,
                        progress = record.progress!!,
                        filePath = record.filePath,
                        error = record.error,
                        createdAt = record.createdAt!!,
                        updatedAt = record.updatedAt!!
                    )
                }
                call.respond(HttpStatusCode.OK, DownloadJobListResponse(items = items, totalCount = result.totalCount))
            }

            patch("/{jobId}/cancel") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val jobId = call.parameters["jobId"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid job ID")

                val existing = downloadJobRepository.findByIdAndUserId(jobId, principal.userId)
                    ?: throw NotFoundException("Download job not found")

                val affected = downloadJobRepository.cancelJob(jobId, principal.userId)
                if (affected == 0) {
                    throw ValidationException("Job cannot be cancelled (status: ${existing.status})")
                }

                call.respond(HttpStatusCode.OK, CancelJobResponse(jobId = jobId, status = "cancelled"))
            }
        }
    }
}

@Serializable
data class DownloadStartedResponse(
    val jobId: Int,
    val status: String
)

@Serializable
data class DownloadStatusResponse(
    val jobId: Int,
    val status: String,
    val progress: Int,
    val filePath: String?,
    val error: String?
)

@Serializable
data class DownloadJobItem(
    val jobId: Int,
    val bookMd5: String,
    val format: String,
    val status: String,
    val progress: Int,
    val filePath: String?,
    val error: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class DownloadJobListResponse(
    val items: List<DownloadJobItem>,
    val totalCount: Long
)

@Serializable
data class CancelJobResponse(
    val jobId: Int,
    val status: String
)
