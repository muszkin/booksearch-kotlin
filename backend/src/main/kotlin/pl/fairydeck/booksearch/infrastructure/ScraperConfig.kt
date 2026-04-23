package pl.fairydeck.booksearch.infrastructure

import io.ktor.server.application.*

data class ScraperConfig(
    val solvearrUrl: String,
    val userAgent: String,
    val requestDelayMs: Long,
    val maxRetries: Int,
    val backoffMultiplier: Double,
    val maxConcurrentDownloads: Int = 2,
    val dataPath: String = "./data/library"
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): ScraperConfig {
            val config = environment.config
            return ScraperConfig(
                solvearrUrl = config.property("scraper.solvearrUrl").getString(),
                userAgent = config.property("scraper.userAgent").getString(),
                requestDelayMs = config.property("scraper.requestDelayMs").getString().toLong(),
                maxRetries = config.property("scraper.maxRetries").getString().toInt(),
                backoffMultiplier = config.property("scraper.backoffMultiplier").getString().toDouble(),
                maxConcurrentDownloads = config.propertyOrNull("scraper.maxConcurrentDownloads")?.getString()?.toInt() ?: 2,
                dataPath = config.propertyOrNull("scraper.dataPath")?.getString() ?: "./data/library"
            )
        }
    }
}
