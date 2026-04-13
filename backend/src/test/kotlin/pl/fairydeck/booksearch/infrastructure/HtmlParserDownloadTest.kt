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

        assertNotNull(fileUrl, "Should find a download link containing first 12 chars of md5")
        assertEquals("https://download.example.com/file/a1b2c3d4e5f6/some-book-title.epub", fileUrl)
    }

    @Test
    fun shouldReturnNullWhenNoMatchingMd5InSlowDownloadPage() {
        val nonExistentMd5 = "ffffffffffffffffffffffffffffffff"
        val fileUrl = HtmlParser.parseSlowDownloadPageFileUrl(slowDownloadPageHtml, nonExistentMd5)

        assertNull(fileUrl, "Should return null when md5 prefix not found in any href")
    }
}
