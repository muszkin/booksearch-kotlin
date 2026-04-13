package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.service.DownloadService

fun Route.downloadRoutes(downloadService: DownloadService) {
    authenticate("jwt") {
        route("/api/download") {
            post("/{md5}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val md5 = call.parameters["md5"]
                    ?: throw ValidationException("Missing md5 parameter")

                val jobId = downloadService.startDownload(principal.userId, md5)
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
