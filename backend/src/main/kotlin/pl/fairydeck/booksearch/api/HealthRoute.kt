package pl.fairydeck.booksearch.api

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String)

fun Route.healthRoutes() {
    get("/api/health") {
        call.respond(HealthResponse(status = "ok"))
    }
}
