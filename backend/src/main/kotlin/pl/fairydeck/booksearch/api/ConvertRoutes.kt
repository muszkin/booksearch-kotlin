package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.service.ActivityLogService
import pl.fairydeck.booksearch.service.ConversionService

fun Route.convertRoutes(conversionService: ConversionService, activityLogService: ActivityLogService) {
    authenticate("jwt") {
        route("/api/convert") {
            post("/{libraryId}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val libraryId = call.parameters["libraryId"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid library entry ID")

                val targetFormat = call.request.queryParameters["target"]
                    ?: throw ValidationException("Missing 'target' query parameter")

                val jobId = conversionService.startConversion(principal.userId, libraryId, targetFormat)
                activityLogService.log(principal.userId, "BOOK_CONVERTED", "library_entry", libraryId.toString(), "target=$targetFormat")
                call.respond(HttpStatusCode.Accepted, ConversionStartedResponse(jobId = jobId, status = "queued"))
            }

            get("/status/{jobId}") {
                val jobId = call.parameters["jobId"]
                    ?: throw ValidationException("Missing job ID")

                val status = conversionService.getJobStatus(jobId)
                    ?: throw NotFoundException("Conversion job not found")

                call.respond(HttpStatusCode.OK, ConversionStatusResponse(
                    jobId = status.jobId,
                    status = status.status,
                    sourceFormat = status.sourceFormat,
                    targetFormat = status.targetFormat,
                    error = status.error
                ))
            }
        }
    }
}

@Serializable
data class ConversionStartedResponse(
    val jobId: String,
    val status: String
)

@Serializable
data class ConversionStatusResponse(
    val jobId: String,
    val status: String,
    val sourceFormat: String,
    val targetFormat: String,
    val error: String?
)
