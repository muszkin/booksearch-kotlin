package pl.fairydeck.booksearch

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.MDC
import pl.fairydeck.booksearch.api.healthRoutes
import pl.fairydeck.booksearch.infrastructure.RequestLoggerPlugin
import java.util.UUID

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureCallId()
    configureRequestLogger()
    configureContentNegotiation()
    configureStatusPages()
    configureRouting()
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

private fun Application.configureRouting() {
    routing {
        healthRoutes()

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
