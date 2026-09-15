package pl.fairydeck.booksearch.infrastructure

import io.ktor.server.application.ApplicationEnvironment

class OpenRouterConfig(
    val baseUrl: String,
    internal val apiKey: String?,
    val model: String = DEFAULT_MODEL
) {
    companion object {
        const val DEFAULT_MODEL = "openrouter/auto"
        fun fromEnvironment(environment: ApplicationEnvironment): OpenRouterConfig {
            val apiKey = System.getenv("OPENROUTER_API_KEY")?.takeIf { it.isNotBlank() }

            return OpenRouterConfig(
                baseUrl = environment.config.propertyOrNull("openRouter.baseUrl")?.getString()
                    ?: "https://openrouter.ai",
                apiKey = apiKey,
                model = environment.config.propertyOrNull("openrouter.model")?.getString()?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL
            )
        }
    }
}
