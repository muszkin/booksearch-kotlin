package pl.fairydeck.booksearch.infrastructure

import io.ktor.http.Headers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class OpenRouterRateLimit(
    val scope: String = "unknown",
    val retryAt: String? = null,
    val limit: Long? = null,
    val remaining: Long? = null
)

/** Only retain allowlisted timing/count fields; upstream prose is never propagated. */
fun parseOpenRouterRateLimit(headers: Headers, body: String, now: Instant = Instant.now()): OpenRouterRateLimit {
    val metadata = runCatching {
        ((Json.parseToJsonElement(body) as? JsonObject)?.get("error") as? JsonObject)?.get("metadata") as? JsonObject
    }.getOrNull()
    val metadataHeaders = metadata?.get("headers") as? JsonObject
    fun header(name: String): String? = headers[name] ?: (metadataHeaders?.entries?.firstOrNull { it.key.equals(name, true) }?.value as? JsonPrimitive)?.contentOrNull
    val retry = header("Retry-After")?.let { value ->
        runCatching {
            value.toLongOrNull()?.takeIf { it >= 0 }?.let(now::plusSeconds)
                ?: ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull()
    }
    val reset = header("X-RateLimit-Reset")?.toLongOrNull()?.let {
        runCatching { if (it >= 100_000_000_000L) Instant.ofEpochMilli(it) else Instant.ofEpochSecond(it) }.getOrNull()
    }
    val platform = listOf("X-RateLimit-Reset", "X-RateLimit-Limit", "X-RateLimit-Remaining").any { header(it) != null }
    val provider = listOf("provider_code", "provider_name").any { (metadata?.get(it) as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true }
    fun count(name: String) = header(name)?.toLongOrNull()?.takeIf { it >= 0 }
    return OpenRouterRateLimit(
        scope = if (provider) "provider" else if (platform) "platform" else "unknown",
        retryAt = listOfNotNull(retry, reset).filter { it.isAfter(now) }.maxOrNull()?.toString(),
        limit = count("X-RateLimit-Limit"), remaining = count("X-RateLimit-Remaining")
    )
}
