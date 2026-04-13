package pl.fairydeck.booksearch.service

import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.infrastructure.ImpersonatorHttpClient
import pl.fairydeck.booksearch.infrastructure.MirrorConfig
import pl.fairydeck.booksearch.repository.MirrorRepository

class MirrorService(
    private val mirrorRepository: MirrorRepository,
    private val httpClient: ImpersonatorHttpClient,
    private val config: MirrorConfig
) {

    private val logger = LoggerFactory.getLogger(MirrorService::class.java)

    fun getActiveMirror(): String? =
        mirrorRepository.findBestWorking()?.baseUrl

    suspend fun checkAllDomains() {
        for (domain in config.domains) {
            val baseUrl = "https://$domain"
            try {
                val startTime = System.currentTimeMillis()
                val result = httpClient.fetch(baseUrl)
                val responseTimeMs = (System.currentTimeMillis() - startTime).toInt()

                val isWorking = result.statusCode in 200..399
                    && !ImpersonatorHttpClient.isChallengePage(result.body)

                mirrorRepository.upsert(domain, baseUrl, isWorking, responseTimeMs)
                logger.info("Mirror {} checked: working={}, responseTime={}ms", domain, isWorking, responseTimeMs)
            } catch (e: Exception) {
                logger.warn("Mirror {} unreachable: {}", domain, e.message)
                mirrorRepository.upsert(domain, baseUrl, false, Int.MAX_VALUE)
            }
        }
    }

    suspend fun refreshMirrors() {
        logger.info("Starting mirror refresh for {} domains", config.domains.size)
        checkAllDomains()
        val bestMirror = getActiveMirror()
        if (bestMirror != null) {
            logger.info("Best working mirror after refresh: {}", bestMirror)
        } else {
            logger.warn("No working mirrors found after refresh")
        }
    }
}
