package pl.fairydeck.booksearch.service

import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.infrastructure.RetentionConfig
import pl.fairydeck.booksearch.repository.DownloadJobRepository
import pl.fairydeck.booksearch.repository.SearchJobRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

class JobRetentionService(
    private val searchJobRepository: SearchJobRepository,
    private val downloadJobRepository: DownloadJobRepository,
    private val config: RetentionConfig
) {

    private val logger = LoggerFactory.getLogger(JobRetentionService::class.java)

    fun sweep() {
        val searchJobsDeleted = searchJobRepository.deleteOlderThan(cutoff(config.searchJobDays))
        val downloadJobsDeleted = downloadJobRepository.deleteTerminalOlderThan(cutoff(config.downloadJobDays))

        logger.info(
            "Job retention sweep removed {} search jobs older than {}d and {} finished download jobs older than {}d",
            searchJobsDeleted,
            config.searchJobDays,
            downloadJobsDeleted,
            config.downloadJobDays
        )
    }

    fun failInterruptedSearchJobs() {
        val failed = searchJobRepository.failNonTerminal(INTERRUPTED_REASON)
        if (failed > 0) {
            logger.info("Marked {} search jobs as failed after restart", failed)
        }
    }

    private fun cutoff(days: Long): Instant = Instant.now().minus(days, ChronoUnit.DAYS)

    private companion object {
        const val INTERRUPTED_REASON = "Search was interrupted by a server restart"
    }
}
