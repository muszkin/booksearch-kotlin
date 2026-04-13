package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pl.fairydeck.booksearch.ErrorResponse
import pl.fairydeck.booksearch.service.SearchService

fun Route.searchRoutes(searchService: SearchService) {
    authenticate("jwt") {
        get("/api/search") {
            val principal = call.principal<UserPrincipal>()
                ?: throw AuthenticationException("Authentication required")

            val query = call.request.queryParameters["q"]
            if (query.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "Query parameter 'q' is required")
                )
                return@get
            }

            val language = call.request.queryParameters["lang"] ?: "pl"
            if (language !in setOf("pl", "en", "de")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Invalid language. Allowed: pl, en, de"))
                return@get
            }

            val format = call.request.queryParameters["ext"] ?: "epub"
            if (format !in setOf("epub", "mobi", "pdf")) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Invalid format. Allowed: epub, mobi, pdf"))
                return@get
            }

            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            if (page < 1) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "Page must be >= 1"))
                return@get
            }

            val maxPages = call.request.queryParameters["maxPages"]?.toIntOrNull() ?: 3

            if (maxPages !in 1..10) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(400, "maxPages must be between 1 and 10")
                )
                return@get
            }

            val response = searchService.search(
                userId = principal.userId,
                query = query,
                language = language,
                format = format,
                page = page,
                maxPages = maxPages
            )

            call.respond(HttpStatusCode.OK, response)
        }
    }
}
