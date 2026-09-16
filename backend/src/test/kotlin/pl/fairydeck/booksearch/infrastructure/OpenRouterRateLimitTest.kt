package pl.fairydeck.booksearch.infrastructure

import io.ktor.http.headersOf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class OpenRouterRateLimitTest {
    private val now = Instant.parse("2026-09-16T12:00:00Z")

    @Test fun `later numeric and platform reset hints win in either epoch unit`() {
        assertEquals("2026-09-16T12:01:30Z", parseOpenRouterRateLimit(headersOf("Retry-After", "90"), "{}", now).retryAt)
        for (reset in listOf("1789560180", "1789560180000")) {
            val result = parseOpenRouterRateLimit(headersOf("Retry-After" to listOf("90"), "X-RateLimit-Reset" to listOf(reset)), "{}", now)
            assertEquals("2026-09-16T12:03:00Z", result.retryAt)
            assertEquals("platform", result.scope)
        }
        assertEquals("2026-09-16T12:04:00Z", parseOpenRouterRateLimit(headersOf("Retry-After" to listOf("240"), "X-RateLimit-Reset" to listOf("1789560180")), "{}", now).retryAt)
    }

    @Test fun `HTTP date provider and unknown metadata are safe`() {
        val result = parseOpenRouterRateLimit(headersOf("Retry-After", "Wed, 16 Sep 2026 12:02:00 GMT"), """{"error":{"metadata":{"provider_name":"secret key prose","raw":"secret"}}}""", now)
        assertEquals("2026-09-16T12:02:00Z", result.retryAt)
        assertEquals("provider", result.scope)
        assertFalse(result.toString().contains("secret"))
        assertEquals("unknown", parseOpenRouterRateLimit(headersOf(), "invalid", now).scope)
    }

    @Test fun `malformed past and overflowing hints are ignored`() {
        for (hint in listOf("garbage", "-1", "99999999999999999999999999", "Wed, 16 Sep 2026 11:59:00 GMT")) {
            assertNull(parseOpenRouterRateLimit(headersOf("Retry-After", hint), "{}", now).retryAt)
        }
        val result = parseOpenRouterRateLimit(headersOf("X-RateLimit-Reset" to listOf("1"), "X-RateLimit-Limit" to listOf("20"), "X-RateLimit-Remaining" to listOf("0")), "{}", now)
        assertNull(result.retryAt)
        assertEquals(20L, result.limit)
        assertEquals(0L, result.remaining)
    }
}
