package pl.fairydeck.booksearch.infrastructure

data class DownloadLink(
    val url: String,
    val label: String,
    val noWaitlist: Boolean
)
