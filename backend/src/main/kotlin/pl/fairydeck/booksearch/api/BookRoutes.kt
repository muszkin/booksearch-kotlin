package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.service.BookDescriptionService

fun Route.bookRoutes(bookDescriptionService: BookDescriptionService) {
    authenticate("jwt") {
        post("/api/books/{md5}/description/regenerate") {
            call.principal<UserPrincipal>() ?: throw AuthenticationException("Authentication required")

            val md5 = call.parameters["md5"]?.takeIf { it.isNotBlank() }
                ?: throw ValidationException("Missing md5 parameter")

            val description = bookDescriptionService.regenerate(md5)
                ?: throw NotFoundException("Could not generate a description for this book")

            call.respond(
                HttpStatusCode.OK,
                BookDescriptionResponse(
                    description = description.description,
                    source = description.source,
                    isbn = description.isbn,
                    canRegenerate = bookDescriptionService.canGenerate
                )
            )
        }

        get("/api/books/{md5}/description") {
            call.principal<UserPrincipal>() ?: throw AuthenticationException("Authentication required")

            val md5 = call.parameters["md5"]?.takeIf { it.isNotBlank() }
                ?: throw ValidationException("Missing md5 parameter")

            val description = bookDescriptionService.describe(md5)
                ?: throw NotFoundException("No description available for this book")

            call.respond(
                HttpStatusCode.OK,
                BookDescriptionResponse(
                    description = description.description,
                    source = description.source,
                    isbn = description.isbn,
                    canRegenerate = bookDescriptionService.canGenerate
                )
            )
        }
    }
}

@Serializable
data class BookDescriptionResponse(
    val description: String,
    val source: String,
    val isbn: String? = null,
    /** False when no OpenRouter key is configured, so the interface can hide the control. */
    val canRegenerate: Boolean = false
)
