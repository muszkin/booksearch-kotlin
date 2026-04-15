package pl.fairydeck.booksearch.repository

data class PaginatedResult<T>(
    val items: List<T>,
    val totalCount: Long
)
