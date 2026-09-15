package pl.fairydeck.booksearch.infrastructure

import io.ktor.server.application.ApplicationEnvironment

class OpenRouterConfig(
    val baseUrl: String,
    internal val apiKey: String
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): OpenRouterConfig {
            val apiKey = System.getenv("OPENROUTER_API_KEY")
                ?.takeIf { it.isNotBlank() }
                ?: throw OpenRouterException("OPENROUTER_API_KEY is required")

            return OpenRouterConfig(
                baseUrl = environment.config.property("openRouter.baseUrl").getString(),
                apiKey = apiKey
            )
        }
    }
}
