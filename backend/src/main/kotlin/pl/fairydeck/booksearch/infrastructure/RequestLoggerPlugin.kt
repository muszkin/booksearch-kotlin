package pl.fairydeck.booksearch.infrastructure

import io.ktor.server.application.*
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.UserPrincipal
import pl.fairydeck.booksearch.repository.RequestLogRepository

private val logger = LoggerFactory.getLogger("pl.fairydeck.booksearch.infrastructure.RequestLoggerPlugin")

private val requestStartTimeKey = AttributeKey<Long>("requestStartTime")

val requestLogRepositoryKey = AttributeKey<RequestLogRepository>("requestLogRepository")

private val sensitiveHeaders = setOf(
    "authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token"
)

private val jsonPrinter = Json {
    prettyPrint = false
    encodeDefaults = true
}

@Serializable
data class RequestLogEntry(
    val method: String,
    val path: String,
    val statusCode: Int,
    val durationMs: Long,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val requestBody: String? = null,
    val responseBody: String? = null,
)

val RequestLoggerPlugin = createApplicationPlugin(name = "RequestLoggerPlugin") {
    on(ResponseSent) { call ->
        val startTime = call.attributes.getOrNull(requestStartTimeKey) ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime

        val requestHeaders = call.request.headers.toMap()
            .filterKeys { it.lowercase() !in sensitiveHeaders }
            .mapValues { it.value.joinToString(", ") }
        val responseHeaders = call.response.headers.allValues().toMap()
            .filterKeys { it.lowercase() !in sensitiveHeaders }
            .mapValues { it.value.joinToString(", ") }

        val logEntry = RequestLogEntry(
            method = call.request.httpMethod.value,
            path = call.request.path(),
            statusCode = call.response.status()?.value ?: 0,
            durationMs = duration,
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders,
        )

        logger.info(jsonPrinter.encodeToString(logEntry))

        val repo = call.application.attributes.getOrNull(requestLogRepositoryKey)
        if (repo != null) {
            val userId = try {
                call.principal<UserPrincipal>()?.userId
            } catch (_: Exception) {
                null
            }
            val requestId = call.callId
            call.application.launch(Dispatchers.IO) {
                try {
                    repo.insert(
                        method = logEntry.method,
                        path = logEntry.path,
                        statusCode = logEntry.statusCode,
                        durationMs = duration.toInt(),
                        requestHeaders = jsonPrinter.encodeToString(logEntry.requestHeaders),
                        responseHeaders = jsonPrinter.encodeToString(logEntry.responseHeaders),
                        requestId = requestId,
                        userId = userId
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to persist request log: {}", e.message)
                }
            }
        }
    }

    onCall { call ->
        call.attributes.put(requestStartTimeKey, System.currentTimeMillis())
    }
}
