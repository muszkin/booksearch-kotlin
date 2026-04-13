package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.service.MirrorService

fun Route.mirrorRoutes(mirrorService: MirrorService) {
    authenticate("jwt") {
        route("/api/mirrors") {
            get("/current") {
                requireSuperAdmin(call)
                val activeMirror = mirrorService.getActiveMirror()
                if (activeMirror != null) {
                    call.respond(HttpStatusCode.OK, MirrorResponse(baseUrl = activeMirror))
                } else {
                    throw NotFoundException("No working mirror available")
                }
            }
        }
    }
}

@Serializable
data class MirrorResponse(val baseUrl: String)
