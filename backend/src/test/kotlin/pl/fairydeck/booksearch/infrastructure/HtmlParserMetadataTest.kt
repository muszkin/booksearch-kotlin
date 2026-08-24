package pl.fairydeck.booksearch.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HtmlParserMetadataTest {

    @Test
    fun readsBookLanguageRatherThanTheLeadingMetadataLanguage() {
        val results = HtmlParser.parseSearchResults(entryWith(META_WITH_YEAR))

        assertEquals(1, results.size)
        assertEquals("Polish [pl]", results[0].language)
    }

    @Test
    fun readsFormatAndSizeFromTheCurrentMetadataLine() {
        val results = HtmlParser.parseSearchResults(entryWith(META_WITH_YEAR))

        assertEquals("epub", results[0].format)
        assertEquals("0.2MB", results[0].fileSize)
        assertEquals("1987", results[0].year)
    }

    @Test
    fun leavesYearBlankWhenTheEntryHasNone() {
        val results = HtmlParser.parseSearchResults(entryWith(META_WITHOUT_YEAR))

        assertEquals("", results[0].year)
        assertEquals("epub", results[0].format)
        assertEquals("0.3MB", results[0].fileSize)
        assertEquals("Polish [pl]", results[0].language)
    }

    @Test
    fun stripsTheVerifiedMarkerFromTheLanguage() {
        val results = HtmlParser.parseSearchResults(entryWith(META_VERIFIED))

        assertEquals("Polish [pl]", results[0].language)
    }

    @Test
    fun stillParsesTheOlderSingleLanguageMetadataLine() {
        val results = HtmlParser.parseSearchResults(entryWith(LEGACY_META))

        assertEquals("German [de]", results[0].language)
        assertEquals("pdf", results[0].format)
        assertEquals("3.5MB", results[0].fileSize)
        assertEquals("2000", results[0].year)
    }

    private fun entryWith(metadataLine: String): String = """
        <div class="js-aarecord-list-outer">
          <div class="flex mb-4">
            <div id="list_cover_aarecord_id__md5:$MD5"><img src="https://covers.example.com/c.jpg"></div>
            <div class="flex-grow">
              <a href="/md5/$MD5" class="js-vim-focus">Solaris</a>
              <div><a href="/search?author=Lem"><span class="icon-[mdi--user-edit]"></span> Stanisław Lem</a></div>
              <div class="font-semibold text-sm">$metadataLine</div>
            </div>
          </div>
        </div>
    """.trimIndent()

    private companion object {
        const val MD5 = "9df6214cb635cca4e2f9ef7707a71b17"
        const val META_WITH_YEAR =
            "English [en] · Polish [pl] · EPUB · 0.2MB · 1987 · 📕 Book (fiction) · 🚀/lgli/upload/zlib"
        const val META_WITHOUT_YEAR =
            "English [en] · Polish [pl] · EPUB · 0.3MB · 📗 Book (unknown) · 🚀/upload/zlib"
        const val META_VERIFIED =
            "English [en] · ✅ Polish [pl] · EPUB · 1.1MB · 1995 · 📗 Book (unknown) · 🚀/upload/zlib"
        const val LEGACY_META =
            "German [de] · PDF · 3.5MB · 2000 · Book (fiction) · /lgli/lgrs"
    }
}
