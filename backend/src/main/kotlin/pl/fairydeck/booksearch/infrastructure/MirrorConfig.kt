package pl.fairydeck.booksearch.infrastructure

import io.ktor.server.application.*

data class MirrorConfig(
    val domains: List<String>,
    val refreshIntervalHours: Int
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): MirrorConfig {
            val config = environment.config
            val domainsRaw = config.property("mirror.domains").getString()
            return MirrorConfig(
                domains = domainsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                refreshIntervalHours = config.property("mirror.refreshIntervalHours").getString().toInt()
            )
        }
    }
}
