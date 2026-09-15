package pl.fairydeck.booksearch.infrastructure

import io.ktor.server.application.*

data class RetentionConfig(
    val searchJobDays: Long = DEFAULT_SEARCH_JOB_DAYS,
    val downloadJobDays: Long = DEFAULT_DOWNLOAD_JOB_DAYS
) {
    companion object {
        const val DEFAULT_SEARCH_JOB_DAYS = 1L
        const val DEFAULT_DOWNLOAD_JOB_DAYS = 30L

        fun fromEnvironment(environment: ApplicationEnvironment): RetentionConfig {
            val config = environment.config
            return RetentionConfig(
                searchJobDays = config.propertyOrNull("retention.searchJobDays")
                    ?.getString()
                    ?.toLongOrNull()
                    ?.coerceAtLeast(1)
                    ?: DEFAULT_SEARCH_JOB_DAYS,
                downloadJobDays = config.propertyOrNull("retention.downloadJobDays")
                    ?.getString()
                    ?.toLongOrNull()
                    ?.coerceAtLeast(1)
                    ?: DEFAULT_DOWNLOAD_JOB_DAYS
            )
        }
    }
}
