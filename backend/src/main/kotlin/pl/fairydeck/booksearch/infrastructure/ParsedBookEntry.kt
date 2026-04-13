package pl.fairydeck.booksearch.infrastructure

data class ParsedBookEntry(
    val md5: String,
    val title: String,
    val author: String,
    val language: String,
    val format: String,
    val fileSize: String,
    val detailUrl: String,
    val coverUrl: String,
    val publisher: String,
    val year: String,
    val description: String
)
