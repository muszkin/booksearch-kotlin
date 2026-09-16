package pl.fairydeck.booksearch.service

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import pl.fairydeck.booksearch.infrastructure.OpenRouterRateLimit
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class TranslationRateLimitPolicyTest {
    @TempDir lateinit var root: Path

    @Test fun `exponential delays survive restart and gate another job`() = runBlocking {
        var now = Instant.parse("2026-09-16T12:00:00Z")
        val waits = mutableListOf<Long>()
        val workspace = EpubTranslationWorkspace(root)
        fun policy() = TranslationRateLimitPolicy(workspace, { now }, { waits.add(it); now = now.plusMillis(it) }, { 0 })
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        for (count in 1..3) {
            policy().record(first, OpenRouterRateLimit(), count)
            assertNotNull(policy().active(second))
            policy().awaitReady(second)
            assertNull(policy().active(first))
        }
        assertEquals(listOf(60_000L, 120_000L, 240_000L), waits)
    }

    @Test fun `fractional millisecond deadline remains gated until cooldown expires`() = runBlocking {
        var now = Instant.parse("2026-09-16T12:00:00Z")
        val deadline = now.plusSeconds(60).plusNanos(500_000)
        val waits = mutableListOf<Long>()
        val policy = TranslationRateLimitPolicy(
            EpubTranslationWorkspace(root), { now },
            { waits.add(it); now = now.plusMillis(it) }, { 0 }
        )
        val jobId = UUID.randomUUID().toString()
        policy.record(jobId, OpenRouterRateLimit(retryAt = deadline.toString()), 1)

        policy.awaitReady(jobId)

        assertFalse(now.isBefore(deadline), "Request eligibility must not precede the server deadline")
        assertNull(policy.active(jobId))
        assertEquals(listOf(60_000L, 1L), waits)
    }

    @Test fun `long server reset and existing shared deadline cannot be shortened`() = runBlocking {
        val now = Instant.parse("2026-09-16T12:00:00Z")
        val policy = TranslationRateLimitPolicy(EpubTranslationWorkspace(root), { now }, { fail("Must not sleep for a day") }, { 0 })
        val first = UUID.randomUUID().toString()
        val second = UUID.randomUUID().toString()
        val deadline = now.plusSeconds(86400).toString()
        policy.record(first, OpenRouterRateLimit("provider", deadline), 1)
        assertEquals(deadline, policy.record(second, OpenRouterRateLimit(), 1).retryAt)
        assertThrows(pl.fairydeck.booksearch.infrastructure.OpenRouterException::class.java) { runBlocking { policy.awaitReady(second) } }
        Unit
    }
}
