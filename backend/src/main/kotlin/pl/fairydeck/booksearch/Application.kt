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
import pl.fairydeck.booksearch.api.convertRoutes
import pl.fairydeck.booksearch.api.deliverRoutes
import pl.fairydeck.booksearch.api.downloadRoutes
import pl.fairydeck.booksearch.api.healthRoutes
import pl.fairydeck.booksearch.api.libraryRoutes
import pl.fairydeck.booksearch.api.mirrorRoutes
import pl.fairydeck.booksearch.api.openApiRoutes
import pl.fairydeck.booksearch.api.searchRoutes
import pl.fairydeck.booksearch.api.logRoutes
import pl.fairydeck.booksearch.api.settingsRoutes
import pl.fairydeck.booksearch.repository.UserSettingsRepository
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.ImpersonatorHttpClient
import pl.fairydeck.booksearch.infrastructure.MirrorConfig
import pl.fairydeck.booksearch.infrastructure.RequestLoggerPlugin
import pl.fairydeck.booksearch.infrastructure.requestLogRepositoryKey
import pl.fairydeck.booksearch.infrastructure.ScraperConfig
import pl.fairydeck.booksearch.infrastructure.SolvearrClient
import pl.fairydeck.booksearch.repository.ActivityLogRepository
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.MirrorRepository
import pl.fairydeck.booksearch.repository.PasswordResetTokenRepository
import pl.fairydeck.booksearch.repository.RefreshTokenRepository
import pl.fairydeck.booksearch.repository.RequestLogRepository
import pl.fairydeck.booksearch.repository.SystemConfigRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.repository.UserRepository
import pl.fairydeck.booksearch.repository.DeliveryRepository
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.service.ActivityLogService
import pl.fairydeck.booksearch.service.AuthService
import pl.fairydeck.booksearch.service.CalibreWrapper
import pl.fairydeck.booksearch.service.ConversionService
import pl.fairydeck.booksearch.service.DeliveryService
import pl.fairydeck.booksearch.service.DownloadService
import pl.fairydeck.booksearch.service.LibraryService
import pl.fairydeck.booksearch.service.MetadataService
import pl.fairydeck.booksearch.service.MirrorService
import pl.fairydeck.booksearch.service.ScraperService
import pl.fairydeck.booksearch.service.SearchService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    val requestLogRepository = RequestLogRepository(dsl)
    attributes.put(requestLogRepositoryKey, requestLogRepository)

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

    val scraperConfig = ScraperConfig.fromEnvironment(environment)
    val mirrorConfig = MirrorConfig.fromEnvironment(environment)
    val mirrorRepository = MirrorRepository(dsl)
    val solvearrClient = SolvearrClient(scraperConfig)
    val mirrorService = MirrorService(mirrorRepository, solvearrClient, mirrorConfig)
    val scraperService = ScraperService(solvearrClient, mirrorService)
    val bookRepository = BookRepository(dsl)
    val userLibraryRepository = UserLibraryRepository(dsl)
    val userSettingsRepository = UserSettingsRepository(dsl)
    val searchService = SearchService(scraperService, bookRepository, userLibraryRepository, scraperConfig.cacheTtlDays)
    val libraryService = LibraryService(userLibraryRepository, bookRepository, scraperConfig)
    val metadataService = MetadataService()
    val downloadJobRepository = DownloadJobRepository(dsl)
    val impersonatorHttpClient = ImpersonatorHttpClient(scraperConfig)
    val downloadService = DownloadService(
        downloadJobRepository = downloadJobRepository,
        bookRepository = bookRepository,
        userLibraryRepository = userLibraryRepository,
        solvearrClient = solvearrClient,
        impersonatorHttpClient = impersonatorHttpClient,
        mirrorService = mirrorService,
        scraperConfig = scraperConfig,
        metadataService = metadataService
    )
    val calibreWrapper = CalibreWrapper()
    val conversionService = ConversionService(
        userLibraryRepository = userLibraryRepository,
        calibreWrapper = calibreWrapper,
        libraryService = libraryService,
        scraperConfig = scraperConfig
    )
    val deliveryRepository = DeliveryRepository(dsl)
    val deliveryService = DeliveryService(
        deliveryRepository = deliveryRepository,
        userSettingsRepository = userSettingsRepository,
        libraryService = libraryService,
        userLibraryRepository = userLibraryRepository
    )

    val activityLogRepository = ActivityLogRepository(dsl)
    val activityLogService = ActivityLogService(activityLogRepository)

    configureRouting(authService, systemConfigRepository, mirrorService, searchService, libraryService, downloadService, conversionService, userSettingsRepository, deliveryService, activityLogService, downloadJobRepository, activityLogRepository, requestLogRepository)

    val mirrorRefreshIntervalMs = mirrorConfig.refreshIntervalHours * 3_600_000L
    launch {
        mirrorService.refreshMirrors()
        while (true) {
            delay(mirrorRefreshIntervalMs)
            try {
                mirrorService.refreshMirrors()
            } catch (e: Exception) {
                log.error("Mirror refresh failed", e)
            }
        }
    }
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
        exception<pl.fairydeck.booksearch.infrastructure.ScraperException> { call, cause ->
            call.application.environment.log.error("Scraper failure", cause)
            call.respond(
                HttpStatusCode.BadGateway,
                ErrorResponse(HttpStatusCode.BadGateway.value, "Search service temporarily unavailable")
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

private fun Application.configureRouting(
    authService: AuthService,
    systemConfigRepository: SystemConfigRepository,
    mirrorService: MirrorService,
    searchService: SearchService,
    libraryService: LibraryService,
    downloadService: DownloadService,
    conversionService: ConversionService,
    userSettingsRepository: UserSettingsRepository,
    deliveryService: DeliveryService,
    activityLogService: ActivityLogService,
    downloadJobRepository: DownloadJobRepository,
    activityLogRepository: ActivityLogRepository,
    requestLogRepository: RequestLogRepository
) {
    routing {
        healthRoutes()
        authRoutes(authService, systemConfigRepository)
        adminRoutes(authService)
        mirrorRoutes(mirrorService)
        searchRoutes(searchService)
        libraryRoutes(libraryService, activityLogService)
        downloadRoutes(downloadService, downloadJobRepository, activityLogService)
        convertRoutes(conversionService, activityLogService)
        deliverRoutes(deliveryService, activityLogService)
        settingsRoutes(userSettingsRepository, activityLogService)
        logRoutes(activityLogRepository, requestLogRepository)
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
