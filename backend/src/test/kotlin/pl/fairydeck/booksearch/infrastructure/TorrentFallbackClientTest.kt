package pl.fairydeck.booksearch.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TorrentFallbackClientTest {

    @Test
    fun shouldExplainTorrentPeerTimeoutWithoutLeakingAria2Output() {
        val output = """
            [#41b1d3 0B/256MiB(0%) CN:0 SD:0 DL:0B]
            Stop downloading torrent due to --bt-stop-timeout option.
            /app/data/torrent-jobs/43/internal-file
        """.trimIndent()

        val message = TorrentFallbackClient.formatFailure(output, 180)

        assertEquals("No torrent data became available within 180 seconds", message)
    }

    @Test
    fun shouldKeepUsefulDiagnosticsForOtherTorrentFailures() {
        val message = TorrentFallbackClient.formatFailure("tracker rejected request", 180)

        assertTrue(message.contains("Torrent fallback stopped"))
        assertTrue(message.contains("tracker rejected request"))
    }
}
