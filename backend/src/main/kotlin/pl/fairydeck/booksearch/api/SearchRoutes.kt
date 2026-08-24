package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.ErrorResponse
import pl.fairydeck.booksearch.service.BookResult
import pl.fairydeck.booksearch.service.SearchService

fun Route.searchRoutes(searchService: SearchService) {
    authenticate("jwt") {
        post("/api/search") {
            val principal = call.principal<UserPrincipal>()
                ?: throw AuthenticationException("Authentication required")

            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "Query parameter 'q' is required")
                )
                return@post
            }

            val language = call.request.queryParameters["lang"] ?: "pl"
            if (language !in ALLOWED_LANGUAGES) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Invalid language. Allowed: pl, en, de"))
                return@post
            }

            val format = call.request.queryParameters["ext"] ?: "epub"
            if (format !in ALLOWED_FORMATS) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Invalid format. Allowed: epub, mobi, pdf"))
                return@post
            }

            val maxPages = call.request.queryParameters["maxPages"]?.toIntOrNull() ?: DEFAULT_MAX_PAGES
            if (maxPages !in MIN_MAX_PAGES..MAX_MAX_PAGES) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "maxPages must be between $MIN_MAX_PAGES and $MAX_MAX_PAGES")
                )
                return@post
            }

            val jobId = searchService.startSearch(principal.userId, query, language, format, maxPages)
            call.respond(HttpStatusCode.Accepted, SearchStartedResponse(jobId = jobId, status = "queued"))
        }

        get("/api/search/status/{jobId}") {
            val principal = call.principal<UserPrincipal>()
                ?: throw AuthenticationException("Authentication required")

            val jobId = call.parameters["jobId"]?.toIntOrNull()
                ?: throw ValidationException("Invalid job ID")

            val status = searchService.getJobStatus(jobId, principal.userId)
                ?: throw NotFoundException("Search job not found")

            call.respond(
                HttpStatusCode.OK,
                SearchJobStatusResponse(
                    jobId = status.id,
                    query = status.query,
                    status = status.status,
                    results = status.results,
                    totalResults = status.totalResults,
                    error = status.error
                )
            )
        }
    }
}

@Serializable
data class SearchStartedResponse(
    val jobId: Int,
    val status: String
)

@Serializable
data class SearchJobStatusResponse(
    val jobId: Int,
    val query: String,
    val status: String,
    val results: List<BookResult>,
    val totalResults: Int,
    val error: String? = null
)

private val ALLOWED_LANGUAGES = setOf("pl", "en", "de")
private val ALLOWED_FORMATS = setOf("epub", "mobi", "pdf")
private const val DEFAULT_MAX_PAGES = 3
private const val MIN_MAX_PAGES = 1
private const val MAX_MAX_PAGES = 10
