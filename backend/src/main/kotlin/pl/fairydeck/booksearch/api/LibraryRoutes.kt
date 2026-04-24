package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.service.ActivityLogService
import pl.fairydeck.booksearch.service.AddToLibraryRequest
import pl.fairydeck.booksearch.service.LibraryService
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun Route.libraryRoutes(libraryService: LibraryService, activityLogService: ActivityLogService) {
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
                activityLogService.log(principal.userId, "BOOK_REMOVED", "library_entry", entryId.toString())
                call.respond(HttpStatusCode.OK, mapOf("message" to "Library entry removed successfully"))
            }

            get("/{id}/file") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val entryId = call.parameters["id"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid library entry ID")

                val fileInfo = libraryService.getFileForEntry(principal.userId, entryId)
                val file = File(fileInfo.absolutePath)

                val contentType = when (fileInfo.format.lowercase()) {
                    "epub" -> ContentType("application", "epub+zip")
                    "mobi" -> ContentType("application", "x-mobipocket-ebook")
                    "pdf" -> ContentType.Application.Pdf
                    else -> ContentType.Application.OctetStream
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "${fileInfo.title}.${fileInfo.format}"
                    ).toString()
                )
                call.respondFile(file)
            }

            post("/covers/backfill") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val result = libraryService.backfillCovers(principal.userId)
                activityLogService.log(
                    principal.userId,
                    "LIBRARY_COVER_BACKFILL",
                    "user",
                    principal.userId.toString()
                )
                call.respond(HttpStatusCode.OK, result)
            }

            get("/{id}/cover") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val entryId = call.parameters["id"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid library entry ID")

                val cover = libraryService.getCoverForEntry(principal.userId, entryId)
                    ?: throw NotFoundException("Cover not available for this library entry")

                call.response.header(HttpHeaders.CacheControl, "private, max-age=86400")
                call.respondFile(cover)
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

            post("/batch-download") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val request = call.receive<BatchDownloadRequest>()
                if (request.ids.isEmpty()) {
                    throw ValidationException("At least one library entry ID is required")
                }

                val files = request.ids.map { entryId ->
                    val fileInfo = libraryService.getFileForEntry(principal.userId, entryId)
                    fileInfo
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "library-books.zip"
                    ).toString()
                )
                call.respondOutputStream(contentType = ContentType("application", "zip")) {
                    ZipOutputStream(this).use { zip ->
                        for (fileInfo in files) {
                            val file = File(fileInfo.absolutePath)
                            if (!file.exists()) continue
                            val entryName = "${fileInfo.title}.${fileInfo.format}"
                            zip.putNextEntry(ZipEntry(entryName))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }
    }
}

@Serializable
data class BatchDownloadRequest(
    val ids: List<Int>
)
