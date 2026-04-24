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
    private const val DOWNLOADS_PANEL_SELECTOR = "div#md5-panel-downloads"
    private const val SLOW_DOWNLOAD_LINK_SELECTOR = "a[href*=slow_download]"
    private const val NO_WAITLIST_MARKER = "no waitlist"
    private const val MD5_PREFIX_LENGTH = 12
    private val MD5_PATTERN = Regex("/md5/([a-f0-9]{32})")

    fun parseSearchResults(html: String): List<ParsedBookEntry> {
        if (html.isBlank()) return emptyList()

        val document = Jsoup.parse(html)
        val containers = document.select(CONTAINER_SELECTOR)
        if (containers.isEmpty()) return emptyList()

        val seen = mutableSetOf<String>()
        val results = mutableListOf<ParsedBookEntry>()
        containers.forEach { container ->
            container.select(ENTRY_SELECTOR).forEach { entry ->
                val parsed = parseEntry(entry) ?: return@forEach
                if (seen.add(parsed.md5)) results.add(parsed)
            }
        }
        return results
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

        // Actual format: "Polish [pl] · EPUB · 0.5MB · 2000 · Book (fiction) · /lgli/lgrs"
        // Order: language · format · fileSize · year · type · source
        return FormatInfo(
            language = parts.getOrElse(0) { "" },
            format = parts.getOrElse(1) { "" }.lowercase(),
            fileSize = parts.getOrElse(2) { "" },
            year = parts.getOrElse(3) { "" }
        )
    }

    fun parseDetailPageDownloadLinks(html: String): List<DownloadLink> {
        if (html.isBlank()) return emptyList()

        val document = Jsoup.parse(html)
        val panel = document.selectFirst(DOWNLOADS_PANEL_SELECTOR) ?: return emptyList()

        val links = panel.select(SLOW_DOWNLOAD_LINK_SELECTOR).map { anchor ->
            val url = anchor.attr("href")
            val text = anchor.text()
            val noWaitlist = text.contains(NO_WAITLIST_MARKER, ignoreCase = true)
            DownloadLink(url = url, label = text.trim(), noWaitlist = noWaitlist)
        }

        return links.sortedByDescending { it.noWaitlist }
    }

    fun parseSlowDownloadPageFileUrl(html: String, md5: String): String? {
        if (html.isBlank()) return null

        val md5Prefix = md5.take(MD5_PREFIX_LENGTH)
        val document = Jsoup.parse(html)

        return document.select("a[href]")
            .map { it.attr("href") }
            .firstOrNull { href ->
                href.contains(md5Prefix) && href.startsWith("http")
            }
    }

    private data class FormatInfo(
        val format: String = "",
        val fileSize: String = "",
        val language: String = "",
        val year: String = ""
    )
}
