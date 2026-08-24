package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.models.ChangePasswordRequest
import pl.fairydeck.booksearch.models.CreateUserRequest
import pl.fairydeck.booksearch.models.StopImpersonationRequest
import pl.fairydeck.booksearch.models.ToggleRegistrationRequest
import pl.fairydeck.booksearch.infrastructure.OpenRouterClient
import pl.fairydeck.booksearch.repository.SystemConfigRepository
import pl.fairydeck.booksearch.service.AuthService

fun Route.adminRoutes(authService: AuthService, systemConfigRepository: SystemConfigRepository) {
    authenticate("jwt") {
        route("/api/admin") {
            get("/description-prompt") {
                requireSuperAdmin(call)
                call.respond(
                    HttpStatusCode.OK,
                    DescriptionPromptResponse(
                        style = systemConfigRepository.getDescriptionStyle(),
                        minLength = systemConfigRepository.getMinDescriptionLength(),
                        isDefault = systemConfigRepository.isDescriptionStyleDefault(),
                        guard = OpenRouterClient.GUARD
                    )
                )
            }

            put("/description-prompt") {
                requireSuperAdmin(call)
                val request = call.receive<DescriptionPromptRequest>()
                if (request.style.isBlank()) {
                    throw ValidationException("The description style must not be empty")
                }
                if (request.minLength < 0) {
                    throw ValidationException("The minimum length must not be negative")
                }
                systemConfigRepository.setDescriptionStyle(request.style)
                systemConfigRepository.setMinDescriptionLength(request.minLength)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Description prompt updated"))
            }

            delete("/description-prompt") {
                requireSuperAdmin(call)
                systemConfigRepository.resetDescriptionStyle()
                systemConfigRepository.setMinDescriptionLength(SystemConfigRepository.DEFAULT_MIN_DESCRIPTION_LENGTH)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Description prompt reset to default"))
            }

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

            post("/users/{id}/impersonate") {
                requireSuperAdmin(call)
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")
                val targetUserId = call.parameters["id"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid user ID")
                val response = authService.startImpersonation(
                    adminUserId = principal.userId,
                    targetUserId = targetUserId
                )
                call.respond(HttpStatusCode.OK, response)
            }

            post("/impersonate/stop") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")
                val originalAdminId = principal.originalAdminId
                    ?: throw AuthorizationException("Not in an impersonation session")
                val impersonatedUserId = principal.userId
                val request = call.receive<StopImpersonationRequest>()
                val response = authService.stopImpersonation(
                    currentRefreshToken = request.refreshToken,
                    originalAdminId = originalAdminId,
                    impersonatedUserId = impersonatedUserId
                )
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}

@Serializable
data class DescriptionPromptResponse(
    val style: String,
    val minLength: Int,
    val isDefault: Boolean,
    /** Shown read-only so an administrator can see what is always appended. */
    val guard: String
)

@Serializable
data class DescriptionPromptRequest(
    val style: String,
    val minLength: Int
)
