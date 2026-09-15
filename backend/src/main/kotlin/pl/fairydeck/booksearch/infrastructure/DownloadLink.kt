package pl.fairydeck.booksearch.infrastructure

data class DownloadLink(
    val url: String,
    val label: String,
    val noWaitlist: Boolean
)

data class TorrentDownloadLink(
    val torrentUrl: String,
    val fileLevel1: String,
    val fileLevel2: String? = null
) {
    val isPacked: Boolean
        get() = !fileLevel2.isNullOrBlank()
}
