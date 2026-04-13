package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pl.fairydeck.booksearch.service.AddToLibraryRequest
import pl.fairydeck.booksearch.service.LibraryService

fun Route.libraryRoutes(libraryService: LibraryService) {
    authenticate("jwt") {
        route("/api/library") {
            get {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val page = (call.request.queryParameters["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
                val pageSize = (call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20).coerceIn(1, 100)

                val response = libraryService.getUserLibrary(principal.userId, page, pageSize)
                call.respond(HttpStatusCode.OK, response)
            }

            post {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val request = call.receive<AddToLibraryRequest>()
                val libraryBook = libraryService.addToLibrary(principal.userId, request.bookMd5, request.format)
                call.respond(HttpStatusCode.Created, libraryBook)
            }

            delete("/{id}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val entryId = call.parameters["id"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid library entry ID")

                libraryService.removeFromLibrary(principal.userId, entryId)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Library entry removed successfully"))
            }

            get("/check") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val md5Param = call.request.queryParameters["md5"]
                if (md5Param.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Query parameter 'md5' is required"))
                    return@get
                }

                val md5s = md5Param.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val ownership = libraryService.checkOwnership(principal.userId, md5s)
                call.respond(HttpStatusCode.OK, ownership)
            }
        }
    }
}
