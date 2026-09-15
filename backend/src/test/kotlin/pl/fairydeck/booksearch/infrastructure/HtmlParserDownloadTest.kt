package pl.fairydeck.booksearch.infrastructure

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HtmlParserDownloadTest {

    private val detailPageHtml = javaClass.classLoader
        .getResource("fixtures/annas-archive-detail-page.html")!!
        .readText()

    private val slowDownloadPageHtml = javaClass.classLoader
        .getResource("fixtures/annas-archive-slow-download-page.html")!!
        .readText()

    @Test
    fun shouldExtractSlowDownloadLinksFromDetailPage() {
        val links = HtmlParser.parseDetailPageDownloadLinks(detailPageHtml)

        assertEquals(3, links.size, "Should find only slow_download links, not fast_download")
        assertTrue(links.all { it.url.contains("slow_download") })
        assertEquals("/slow_download/a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6/0/2", links[0].url)
    }

    @Test
    fun shouldPreferNoWaitlistServers() {
        val links = HtmlParser.parseDetailPageDownloadLinks(detailPageHtml)

        val noWaitlistLinks = links.filter { it.noWaitlist }
        assertEquals(2, noWaitlistLinks.size, "Should identify 2 no-waitlist servers")

        val firstLink = links.first()
        assertTrue(firstLink.noWaitlist, "First link should be no-waitlist (sorted to front)")
    }

    @Test
    fun shouldExtractFileUrlFromSlowDownloadPage() {
        val md5 = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
        val fileUrl = HtmlParser.parseSlowDownloadPageFileUrl(slowDownloadPageHtml, md5)

        assertNotNull(fileUrl, "Should find the structurally marked temporary download link")
        assertEquals(
            "https://download.example.com/anon/temporary-token/some-book-title.epub",
            fileUrl
        )
    }

    @Test
    fun shouldReturnNullWhenNoMatchingMd5InSlowDownloadPage() {
        val nonExistentMd5 = "ffffffffffffffffffffffffffffffff"
        val htmlWithoutStructuredLink = """
            <html><body>
              <a href="https://download.example.com/file/a1b2c3d4e5f6/book.epub">Other file</a>
            </body></html>
        """.trimIndent()
        val fileUrl = HtmlParser.parseSlowDownloadPageFileUrl(
            htmlWithoutStructuredLink,
            nonExistentMd5
        )

        assertNull(fileUrl, "Should return null when md5 prefix not found in any href")
    }

    @Test
    fun shouldExtractWaitSecondsFromDownloadSlotPage() {
        val html = """
            <html><body>
              <span class="js-partner-countdown">42</span>
              <script>let waitSeconds = 42;</script>
            </body></html>
        """.trimIndent()

        assertEquals(42, HtmlParser.parseSlowDownloadWaitSeconds(html))
    }

    @Test
    fun shouldExtractPublicTorrentFallbackFromDetailPage() {
        val html = """
            <html><body>
              <ul>
                <li class="list-disc">
                  Bulk torrent downloads
                  <a href="/dyn/small_file/torrents/managed/book-files.torrent">
                    book-files.torrent
                  </a>
                  → file “aacid__book_file”
                </li>
              </ul>
            </body></html>
        """.trimIndent()

        val links = HtmlParser.parseTorrentDownloadLinks(html)

        assertEquals(
            listOf(
                TorrentDownloadLink(
                    torrentUrl = "/dyn/small_file/torrents/managed/book-files.torrent",
                    fileLevel1 = "aacid__book_file"
                )
            ),
            links
        )
    }

    @Test
    fun shouldMarkPackedTorrentFallbackAsUnsupported() {
        val html = """
            <li>
              <a href="/dyn/small_file/torrents/managed/archive.torrent">archive.torrent</a>
              → file “archive.tar” (extract) → file “book.epub”
            </li>
        """.trimIndent()

        val link = HtmlParser.parseTorrentDownloadLinks(html).single()

        assertEquals("archive.tar", link.fileLevel1)
        assertEquals("book.epub", link.fileLevel2)
        assertTrue(link.isPacked)
    }
}
