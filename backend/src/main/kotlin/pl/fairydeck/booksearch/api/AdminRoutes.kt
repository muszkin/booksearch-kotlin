package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pl.fairydeck.booksearch.models.ChangePasswordRequest
import pl.fairydeck.booksearch.models.CreateUserRequest
import pl.fairydeck.booksearch.models.ToggleRegistrationRequest
import pl.fairydeck.booksearch.service.AuthService

fun Route.adminRoutes(authService: AuthService) {
    authenticate("jwt") {
        route("/api/admin") {
            put("/registration") {
                requireSuperAdmin(call)
                val request = call.receive<ToggleRegistrationRequest>()
                authService.toggleRegistration(request.enabled)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Registration toggled successfully"))
            }

            get("/users") {
                requireSuperAdmin(call)
                val users = authService.listUsers()
                call.respond(HttpStatusCode.OK, users)
            }

            post("/users") {
                requireSuperAdmin(call)
                val request = call.receive<CreateUserRequest>()
                val user = authService.createUser(request.email, request.displayName, request.password)
                call.respond(HttpStatusCode.Created, user)
            }

            put("/users/{id}/password") {
                requireSuperAdmin(call)
                val targetUserId = call.parameters["id"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid user ID")
                val request = call.receive<ChangePasswordRequest>()
                authService.changeUserPassword(targetUserId, request.newPassword)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Password changed successfully"))
            }
        }
    }
}

