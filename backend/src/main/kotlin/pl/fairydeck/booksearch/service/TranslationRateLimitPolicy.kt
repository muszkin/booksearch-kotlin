package pl.fairydeck.booksearch.service

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import pl.fairydeck.booksearch.infrastructure.OpenRouterException
import pl.fairydeck.booksearch.infrastructure.OpenRouterRateLimit
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.*
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/** One configured key: provider and unknown limits deliberately gate every translation. */
class TranslationRateLimitPolicy(
    workspace: EpubTranslationWorkspace,
    private val now: () -> Instant = Instant::now,
    private val wait: suspend (Long) -> Unit = { delay(it) },
    private val jitter: () -> Long = { Random.nextLong(1001) }
) {
    private val directory = workspace.rateLimitDirectory
    private fun jobPath(jobId: String): Path = directory.resolve("${UUID.fromString(jobId)}.json")
    private fun read(path: Path): OpenRouterRateLimit? =
        if (Files.exists(path)) Json.decodeFromString<OpenRouterRateLimit>(Files.readString(path)) else null
    private fun deadline(record: OpenRouterRateLimit?) = record?.retryAt?.let(Instant::parse)
    private fun latest(jobId: String): OpenRouterRateLimit? =
        listOfNotNull(read(directory.resolve("shared.json")), read(jobPath(jobId))).maxByOrNull { deadline(it) ?: Instant.MIN }

    fun active(jobId: String): OpenRouterRateLimit? = synchronized(lock) {
        latest(jobId)?.takeIf { deadline(it)?.isAfter(now()) == true }
    }

    fun record(jobId: String, metadata: OpenRouterRateLimit, failures: Int): OpenRouterRateLimit = synchronized(lock) {
        val local = now().plusMillis(60_000L * (1L shl (failures.coerceIn(1, 3) - 1)) + jitter().coerceIn(0, 1000))
        val until = listOfNotNull(local, deadline(metadata), deadline(latest(jobId))).maxOrNull()!!
        val record = metadata.copy(retryAt = until.toString())
        Files.createDirectories(directory)
        // Persist the shared gate first so an interrupted job-record write cannot bypass it.
        write(directory.resolve("shared.json"), record)
        write(jobPath(jobId), record)
        record
    }

    suspend fun awaitReady(jobId: String) {
        while (true) {
            val record = active(jobId) ?: return
            val millis = Duration.between(now(), deadline(record)).toMillis()
            if (millis > 300_000) throw limited(record)
            wait(millis.coerceAtLeast(1))
        }
    }

    private fun write(path: Path, record: OpenRouterRateLimit) {
        val temporary = Files.createTempFile(directory, ".cooldown-", ".tmp")
        try {
            Files.writeString(temporary, Json.encodeToString(record))
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    companion object {
        private val lock = Any()
        fun limited(record: OpenRouterRateLimit) = OpenRouterException("OpenRouter rate limit cooldown is active", true, "http_429", record)
    }
}
