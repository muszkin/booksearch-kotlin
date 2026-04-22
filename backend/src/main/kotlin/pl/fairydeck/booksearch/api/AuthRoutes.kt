package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pl.fairydeck.booksearch.models.ChangeOwnPasswordRequest
import pl.fairydeck.booksearch.models.LoginRequest
import pl.fairydeck.booksearch.models.LogoutRequest
import pl.fairydeck.booksearch.models.PasswordResetBody
import pl.fairydeck.booksearch.models.PasswordResetRequestBody
import pl.fairydeck.booksearch.models.RefreshRequest
import pl.fairydeck.booksearch.models.RegisterRequest
import pl.fairydeck.booksearch.repository.SystemConfigRepository
import pl.fairydeck.booksearch.service.AuthService

fun Route.authRoutes(authService: AuthService, systemConfigRepository: SystemConfigRepository) {
    route("/api/auth") {
        get("/registration-status") {
            val enabled = systemConfigRepository.isRegistrationEnabled()
            call.respond(HttpStatusCode.OK, mapOf("enabled" to enabled))
        }

        post("/register") {
            val request = call.receive<RegisterRequest>()
            val response = authService.register(request.email, request.password, request.displayName)
            call.respond(HttpStatusCode.Created, response)
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            val response = authService.login(request.email, request.password)
            call.respond(HttpStatusCode.OK, response)
        }

        post("/refresh") {
            val request = call.receive<RefreshRequest>()
            val response = authService.refresh(request.refreshToken)
            call.respond(HttpStatusCode.OK, response)
        }

        post("/password-reset-request") {
            val request = call.receive<PasswordResetRequestBody>()
            authService.requestPasswordReset(request.email)
            call.respond(HttpStatusCode.OK, mapOf("message" to "If an account exists, a reset link has been sent"))
        }

        post("/password-reset") {
            val request = call.receive<PasswordResetBody>()
            authService.resetPassword(request.token, request.newPassword)
            call.respond(HttpStatusCode.OK, mapOf("message" to "Password reset successfully"))
        }

        authenticate("jwt") {
            get("/me") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")
                val user = authService.getCurrentUser(principal.userId)
                call.respond(HttpStatusCode.OK, user)
            }

            put("/password") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")
                val request = call.receive<ChangeOwnPasswordRequest>()
                authService.changePassword(principal.userId, request.currentPassword, request.newPassword)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Password changed successfully"))
            }

            post("/logout") {
                val request = call.receive<LogoutRequest>()
                authService.logout(request.refreshToken)
                call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out successfully"))
            }
        }
    }
}
