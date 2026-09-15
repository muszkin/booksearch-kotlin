package pl.fairydeck.booksearch.infrastructure

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImpersonatorHttpClientTest {

    @Test
    fun shouldNotRejectAnnaPageThatMentionsDdosGuard() {
        val html = """
            <html>
              <head><title>Granat poproszę - Anna's Archive</title></head>
              <body>
                <a href="/dyn/small_file/torrents/book.torrent">Public torrent</a>
                <p>DDoS-Guard is mentioned in normal page diagnostics.</p>
              </body>
            </html>
        """.trimIndent()

        assertFalse(ImpersonatorHttpClient.isChallengePage(html))
    }

    @Test
    fun shouldRecognizeDdosGuardChallengeTitle() {
        val html = """
            <html>
              <head><title>DDoS-Guard</title></head>
              <body>Please enable JavaScript to continue.</body>
            </html>
        """.trimIndent()

        assertTrue(ImpersonatorHttpClient.isChallengePage(html))
    }

    @Test
    fun shouldRecognizeChallengeBodyMarker() {
        val html = """
            <html>
              <head><title>Verification</title></head>
              <body>Checking your browser before accessing the website.</body>
            </html>
        """.trimIndent()

        assertTrue(ImpersonatorHttpClient.isChallengePage(html))
    }
}
