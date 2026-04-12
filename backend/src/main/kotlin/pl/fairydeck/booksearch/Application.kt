package pl.fairydeck.booksearch

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.slf4j.MDC
import pl.fairydeck.booksearch.api.AuthenticationException
import pl.fairydeck.booksearch.api.AuthorizationException
import pl.fairydeck.booksearch.api.ConflictException
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.api.UserPrincipal
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.api.adminRoutes
import pl.fairydeck.booksearch.api.authRoutes
import pl.fairydeck.booksearch.api.healthRoutes
import pl.fairydeck.booksearch.api.openApiRoutes
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.RequestLoggerPlugin
import pl.fairydeck.booksearch.repository.PasswordResetTokenRepository
import pl.fairydeck.booksearch.repository.RefreshTokenRepository
import pl.fairydeck.booksearch.repository.SystemConfigRepository
import pl.fairydeck.booksearch.repository.UserRepository
import pl.fairydeck.booksearch.service.AuthService
import java.util.UUID

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureCallId()
    configureRequestLogger()
    configureContentNegotiation()
    configureStatusPages()

    val dsl = configureDatabase()

    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    configureAuthentication(jwtSecret, jwtIssuer, jwtAudience)

    val userRepository = UserRepository(dsl)
    val refreshTokenRepository = RefreshTokenRepository(dsl)
    val passwordResetTokenRepository = PasswordResetTokenRepository(dsl)
    val systemConfigRepository = SystemConfigRepository(dsl)

    val accessTokenExpirationMs = environment.config.property("jwt.accessTokenExpirationMs").getString().toLong()
    val refreshTokenExpirationMs = environment.config.property("jwt.refreshTokenExpirationMs").getString().toLong()

    val databasePath = environment.config.property("database.path").getString()
    if (databasePath != ":memory:" && jwtSecret == "dev-secret-change-in-production") {
        log.warn("JWT secret is using default value! Set JWT_SECRET environment variable for production.")
    }

    val authService = AuthService(
        userRepository = userRepository,
        refreshTokenRepository = refreshTokenRepository,
        passwordResetTokenRepository = passwordResetTokenRepository,
        systemConfigRepository = systemConfigRepository,
        jwtSecret = jwtSecret,
        jwtIssuer = jwtIssuer,
        jwtAudience = jwtAudience,
        accessTokenExpirationMs = accessTokenExpirationMs,
        refreshTokenExpirationMs = refreshTokenExpirationMs
    )

    configureRouting(authService)
}

private fun Application.configureDatabase(): DSLContext {
    val databasePath = environment.config.property("database.path").getString()
    return if (databasePath == ":memory:") {
        DatabaseFactory.createInMemory()
    } else {
        DatabaseFactory.create(databasePath)
    }
}

private fun Application.configureAuthentication(jwtSecret: String, jwtIssuer: String, jwtAudience: String) {
    install(Authentication) {
        jwt("jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                val userId = credential.payload.subject?.toIntOrNull() ?: return@validate null
                val email = credential.payload.getClaim("email")?.asString() ?: return@validate null
                val isSuperAdmin = credential.payload.getClaim("is_super_admin")?.asBoolean() ?: false
                UserPrincipal(userId = userId, email = email, isSuperAdmin = isSuperAdmin)
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(HttpStatusCode.Unauthorized.value, "Invalid or expired token")
                )
            }
        }
    }
}

private fun Application.configureCallId() {
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() }
        replyToHeader(HttpHeaders.XRequestId)
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        val callId = call.callId
        if (callId != null) {
            MDC.put("x-request-id", callId)
        }
        try {
            proceed()
        } finally {
            MDC.remove("x-request-id")
        }
    }
}

private fun Application.configureRequestLogger() {
    install(RequestLoggerPlugin)
}

private fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            isLenient = false
            ignoreUnknownKeys = true
        })
    }
}

private fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AuthenticationException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(HttpStatusCode.Unauthorized.value, cause.message ?: "Unauthorized")
            )
        }
        exception<AuthorizationException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(HttpStatusCode.Forbidden.value, cause.message ?: "Forbidden")
            )
        }
        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ErrorResponse(HttpStatusCode.Conflict.value, cause.message ?: "Conflict")
            )
        }
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ErrorResponse(HttpStatusCode.UnprocessableEntity.value, cause.message ?: "Validation error")
            )
        }
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(HttpStatusCode.NotFound.value, cause.message ?: "Not found")
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    status = HttpStatusCode.InternalServerError.value,
                    message = "Internal server error"
                )
            )
        }
    }
}

private fun Application.configureRouting(authService: AuthService) {
    routing {
        healthRoutes()
        authRoutes(authService)
        adminRoutes(authService)
        openApiRoutes()

        route("/api/{...}") {
            handle {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(404, "Not found"))
            }
        }

        singlePageApplication {
            useResources = true
            filesPath = "static"
            defaultPage = "index.html"
        }
    }
}

@Serializable
data class ErrorResponse(val status: Int, val message: String)
