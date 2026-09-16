package pl.fairydeck.booksearch.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.Json
import pl.fairydeck.booksearch.service.TranslationOptions
import pl.fairydeck.booksearch.infrastructure.OpenRouterClient
import pl.fairydeck.booksearch.infrastructure.OpenRouterException
import pl.fairydeck.booksearch.models.*
import pl.fairydeck.booksearch.service.TranslationService
import pl.fairydeck.booksearch.service.TranslationStatus

fun Route.translationRoutes(service: TranslationService, openRouter: OpenRouterClient?, downloads: pl.fairydeck.booksearch.service.DownloadService? = null) {
    authenticate("jwt") {
        route("/api/translation") {
            get("/models") {
                call.respond(freeTranslationModels(openRouter).map { TranslationModel(it.id, it.name) })
            }
            get("/{libraryId}/estimate") {
                val estimate = service.estimate(call.translationUserId(), call.translationLibraryId(), call.request.queryParameters["modelId"])
                call.respond(TranslationEstimateResponse(estimate.totalChapters, estimate.estimatedInputTokens,
                    estimate.modelId, estimate.indicativeDuration, estimate.limitWarning))
            }
            get("/{libraryId}/references") {
                call.respond(service.references(call.translationUserId(), call.translationLibraryId()))
            }
            post("/{libraryId}/context-preview") {
                call.respond(service.previewContext(call.translationUserId(), call.translationLibraryId(), call.receiveTranslationRequest<TranslationOptions>()))
            }
            post("/{libraryId}/references/{md5}/download") {
                val user = call.translationUserId()
                val md5 = call.parameters["md5"]!!
                service.validateReferenceDownload(user, call.translationLibraryId(), md5)
                val job = (downloads ?: throw ValidationException("Downloads unavailable")).startDownload(user, md5)
                call.respond(HttpStatusCode.Accepted, DownloadStartedResponse(jobId = job, status = "queued"))
            }
            post("/{libraryId}") {
                val body = call.receiveTranslationRequest<JsonObject>()
                val confirmation = body["externalProcessingConfirmed"] as? JsonPrimitive
                val request = TranslationStartRequest(confirmation?.takeUnless { it.isString }?.booleanOrNull
                    ?: throw ValidationException("External processing confirmation must be a boolean"))
                val options = body["options"]?.let {
                    try { Json.decodeFromString<TranslationOptions>(it.toString()) } catch (_: Exception) { throw ValidationException("Invalid translation options") }
                } ?: TranslationOptions()
                val started = service.start(call.translationUserId(), call.translationLibraryId(), request.externalProcessingConfirmed, options)
                call.respond(HttpStatusCode.Accepted, TranslationStartedResponse(started.jobId, TranslationJobState.QUEUED))
            }
            get("/jobs") {
                call.respond(service.listActive(call.translationUserId()).map { it.toResponse() })
            }
            get("/jobs/{jobId}") {
                call.respond(service.status(call.translationUserId(), call.parameters["jobId"]!!).toResponse())
            }
            get("/jobs/{jobId}/details") {
                call.respond(service.details(call.translationUserId(), call.parameters["jobId"]!!))
            }
            get("/jobs/{jobId}/chapters/{index}/export") {
                val index = call.parameters["index"]?.toIntOrNull() ?: throw ValidationException("Invalid chapter index")
                val format = call.request.queryParameters["format"] ?: "txt"
                val text = service.exportChapter(call.translationUserId(), call.parameters["jobId"]!!, index, format)
                call.response.headers.append(HttpHeaders.ContentDisposition, "attachment; filename=chapter-${index + 1}.$format")
                call.respondText(text, ContentType.Text.Plain)
            }
            post("/jobs/{jobId}/resume") {
                val raw = call.receiveText()
                val options = if (raw.isBlank()) null else try { Json.decodeFromString<TranslationOptions>(raw) }
                    catch (_: Exception) { throw ValidationException("Invalid translation options") }
                val started = service.resume(call.translationUserId(), call.parameters["jobId"]!!, options)
                call.respond(HttpStatusCode.Accepted, TranslationStartedResponse(started.jobId, TranslationJobState.QUEUED))
            }
            post("/jobs/{jobId}/cancel") {
                service.cancel(call.translationUserId(), call.parameters["jobId"]!!)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun TranslationStatus.toResponse(): TranslationStatusResponse {
    val status = this
    return TranslationStatusResponse(
        jobId = status.jobId, status = TranslationJobState.valueOf(status.status.uppercase()),
        sourceLibraryEntryId = status.sourceLibraryEntryId, modelId = status.modelId,
        totalChapters = status.totalChapters, completedChapters = status.completedChapters,
        estimatedInputTokens = status.estimatedInputTokens, actualInputTokens = status.actualInputTokens,
        actualOutputTokens = status.actualOutputTokens, resumable = status.resumable,
        failedChapterIndex = status.failedChapterIndex, outputLibraryEntryId = status.outputLibraryEntryId,
        error = status.error
    )
}

internal suspend fun freeTranslationModels(client: OpenRouterClient?) = try {
    (client ?: throw ValidationException("Translation is not configured")).listFreeTextModels()
} catch (_: OpenRouterException) {
    throw ValidationException("Could not load free translation models")
}

internal suspend inline fun <reified T : Any> ApplicationCall.receiveTranslationRequest(): T = try {
    receive<T>()
} catch (_: BadRequestException) {
    throw ValidationException("Invalid translation request")
} catch (_: SerializationException) {
    throw ValidationException("Invalid translation request")
}

private fun ApplicationCall.translationUserId() = principal<UserPrincipal>()?.userId
    ?: throw AuthenticationException("Authentication required")

private fun ApplicationCall.translationLibraryId() = parameters["libraryId"]?.toIntOrNull()?.takeIf { it > 0 }
    ?: throw ValidationException("Invalid library entry ID")
