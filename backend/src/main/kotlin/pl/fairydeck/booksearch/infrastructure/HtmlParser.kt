package pl.fairydeck.booksearch.infrastructure

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object HtmlParser {

    private const val CONTAINER_SELECTOR = "div.js-aarecord-list-outer"
    private const val ENTRY_SELECTOR = "div.flex"
    private const val TITLE_SELECTOR = "a.js-vim-focus"
    private const val AUTHOR_ICON_CLASS = "icon-[mdi--user-edit]"
    private const val PUBLISHER_ICON_CLASS = "icon-[mdi--company]"
    private const val FORMAT_INFO_SELECTOR = "div.font-semibold.text-sm"
    private val MD5_PATTERN = Regex("/md5/([a-f0-9]{32})")

    fun parseSearchResults(html: String): List<ParsedBookEntry> {
        if (html.isBlank()) return emptyList()

        val document = Jsoup.parse(html)
        val container = document.selectFirst(CONTAINER_SELECTOR) ?: return emptyList()

        return container.select("> $ENTRY_SELECTOR").mapNotNull { entry ->
            parseEntry(entry)
        }
    }

    private fun parseEntry(entry: Element): ParsedBookEntry? {
        val titleAnchor = entry.selectFirst(TITLE_SELECTOR) ?: return null
        val href = titleAnchor.attr("href")
        val md5Match = MD5_PATTERN.find(href) ?: return null
        val md5 = md5Match.groupValues[1]

        val title = titleAnchor.text().trim()
        val author = extractTextNearIcon(entry, AUTHOR_ICON_CLASS)
        val publisher = extractTextNearIcon(entry, PUBLISHER_ICON_CLASS)
        val coverUrl = extractCoverUrl(entry, md5)
        val description = extractDescription(entry)

        val formatInfo = parseFormatInfo(entry)

        return ParsedBookEntry(
            md5 = md5,
            title = title,
            author = author,
            language = formatInfo.language,
            format = formatInfo.format,
            fileSize = formatInfo.fileSize,
            detailUrl = "/md5/$md5",
            coverUrl = coverUrl,
            publisher = publisher,
            year = formatInfo.year,
            description = description
        )
    }

    private fun extractTextNearIcon(entry: Element, iconClass: String): String {
        val escapedClass = iconClass.replace("[", "\\[").replace("]", "\\]")
        val iconSpan = entry.selectFirst("span.$escapedClass") ?: return ""
        val parentAnchor = iconSpan.parent() ?: return ""
        return parentAnchor.text().trim()
    }

    private fun extractCoverUrl(entry: Element, md5: String): String {
        val coverDiv = entry.selectFirst("div[id^=list_cover_aarecord_id__md5:$md5]")
            ?: return ""
        return coverDiv.selectFirst("img")?.attr("src") ?: ""
    }

    private fun extractDescription(entry: Element): String {
        val descDiv = entry.selectFirst("div.text-sm.text-gray-500")
        return descDiv?.text()?.trim() ?: ""
    }

    private fun parseFormatInfo(entry: Element): FormatInfo {
        val infoDiv = entry.selectFirst(FORMAT_INFO_SELECTOR) ?: return FormatInfo()
        val parts = infoDiv.text().split("·").map { it.trim() }

        return FormatInfo(
            format = parts.getOrElse(0) { "" }.lowercase(),
            fileSize = parts.getOrElse(1) { "" },
            language = parts.getOrElse(2) { "" },
            year = parts.getOrElse(3) { "" }
        )
    }

    private data class FormatInfo(
        val format: String = "",
        val fileSize: String = "",
        val language: String = "",
        val year: String = ""
    )
}
