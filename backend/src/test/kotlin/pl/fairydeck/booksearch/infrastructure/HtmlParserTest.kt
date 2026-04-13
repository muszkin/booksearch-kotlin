package pl.fairydeck.booksearch.infrastructure

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HtmlParserTest {

    private val fixtureHtml = javaClass.classLoader
        .getResource("fixtures/annas-archive-search-results.html")!!
        .readText()

    @Test
    fun shouldExtractMd5FromHref() {
        val results = HtmlParser.parseSearchResults(fixtureHtml)

        assertTrue(results.isNotEmpty(), "Should parse at least one result")
        assertEquals("a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", results[0].md5)
        assertEquals("b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6a1", results[1].md5)
        assertEquals("c3d4e5f6a7b8c9d0e1f2a3b4c5d6a1b2", results[2].md5)
    }

    @Test
    fun shouldExtractTitleFromVimFocusAnchor() {
        val results = HtmlParser.parseSearchResults(fixtureHtml)

        assertEquals("Przestrzeń objawienia", results[0].title)
        assertEquals("Solaris", results[1].title)
        assertEquals("Dune: Messiah", results[2].title)
    }

    @Test
    fun shouldExtractAuthorFromUserEditIcon() {
        val results = HtmlParser.parseSearchResults(fixtureHtml)

        assertEquals("Alastair Reynolds", results[0].author)
        assertEquals("", results[1].author, "Missing author should be empty string")
        assertEquals("Frank Herbert", results[2].author)
    }

    @Test
    fun shouldExtractFormatSizeAndLanguageFromSemiboldDiv() {
        val results = HtmlParser.parseSearchResults(fixtureHtml)

        assertEquals("epub", results[0].format)
        assertEquals("1.2MB", results[0].fileSize)
        assertEquals("Polish [pl]", results[0].language)

        assertEquals("pdf", results[1].format)
        assertEquals("3.5MB", results[1].fileSize)
        assertEquals("English [en]", results[1].language)

        assertEquals("mobi", results[2].format)
        assertEquals("850KB", results[2].fileSize)
        assertEquals("English [en]", results[2].language)
    }

    @Test
    fun shouldReturnEmptyListForMalformedHtml() {
        val emptyResults = HtmlParser.parseSearchResults("")
        assertTrue(emptyResults.isEmpty(), "Empty HTML should return empty list")

        val noContainerResults = HtmlParser.parseSearchResults("<html><body><p>No results</p></body></html>")
        assertTrue(noContainerResults.isEmpty(), "HTML without container should return empty list")

        val brokenResults = HtmlParser.parseSearchResults("<div class=\"js-aarecord-list-outer\"></div>")
        assertTrue(brokenResults.isEmpty(), "Empty container should return empty list")
    }

    @Test
    fun shouldHandleMultipleResultsWithEdgeCases() {
        val results = HtmlParser.parseSearchResults(fixtureHtml)

        assertEquals(4, results.size, "Should parse all 4 entries from fixture")

        val missingAuthor = results[1]
        assertEquals("", missingAuthor.author)
        assertEquals("Solaris", missingAuthor.title)

        val missingCover = results[2]
        assertEquals("", missingCover.coverUrl, "Entry without cover image should have empty coverUrl")

        val minimalEntry = results[3]
        assertEquals("d4e5f6a7b8c9d0e1f2a3b4c5d6a1b2c3", minimalEntry.md5)
        assertEquals("Unknown Book Title", minimalEntry.title)
        assertEquals("epub", minimalEntry.format)
        assertEquals("German [de]", minimalEntry.language)
    }
}
