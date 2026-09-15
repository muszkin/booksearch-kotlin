package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pl.fairydeck.booksearch.models.ChangePasswordRequest
import pl.fairydeck.booksearch.models.CreateUserRequest
import pl.fairydeck.booksearch.models.StopImpersonationRequest
import pl.fairydeck.booksearch.models.ToggleRegistrationRequest
import pl.fairydeck.booksearch.service.AuthService
import pl.fairydeck.booksearch.infrastructure.OpenRouterClient
import pl.fairydeck.booksearch.models.TranslationConfigRequest
import pl.fairydeck.booksearch.models.TranslationConfigResponse
import pl.fairydeck.booksearch.repository.SystemConfigRepository

fun Route.adminRoutes(authService: AuthService, systemConfig: SystemConfigRepository, openRouter: OpenRouterClient?) {
    authenticate("jwt") {
        route("/api/admin") {
            get("/translation/config") {
                requireSuperAdmin(call)
                val modelId = systemConfig.getTranslationDefaultModel()
                val configured = openRouter != null && !modelId.isNullOrBlank()
                val eligible = if (!configured) false else try {
                    freeTranslationModels(openRouter).any { it.id == modelId }
                } catch (_: ValidationException) { null }
                call.respond(TranslationConfigResponse(modelId, configured, eligible))
            }

            put("/translation/config") {
                requireSuperAdmin(call)
                val request = call.receiveTranslationRequest<TranslationConfigRequest>()
                if (request.defaultModelId.isBlank() || freeTranslationModels(openRouter).none { it.id == request.defaultModelId }) {
                    throw ValidationException("Select a currently free text translation model")
                }
                systemConfig.setTranslationDefaultModel(request.defaultModelId)
                call.respond(TranslationConfigResponse(request.defaultModelId, configured = true, eligible = true))
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
