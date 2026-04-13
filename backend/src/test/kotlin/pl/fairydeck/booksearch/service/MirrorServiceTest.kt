package pl.fairydeck.booksearch.service

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.infrastructure.HttpResult
import pl.fairydeck.booksearch.infrastructure.ImpersonatorHttpClient
import pl.fairydeck.booksearch.infrastructure.MirrorConfig
import pl.fairydeck.booksearch.repository.MirrorRepository

class MirrorServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var mirrorRepository: MirrorRepository
    private lateinit var httpClient: ImpersonatorHttpClient
    private lateinit var mirrorConfig: MirrorConfig
    private lateinit var mirrorService: MirrorService

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        mirrorRepository = MirrorRepository(dsl)
        httpClient = mockk()
        mirrorConfig = MirrorConfig(
            domains = listOf("annas-archive.gd", "annas-archive.pk", "annas-archive.gl"),
            refreshIntervalHours = 24
        )
        mirrorService = MirrorService(mirrorRepository, httpClient, mirrorConfig)
    }

    @Test
    fun upsertStoresMirrorWithAllFields() {
        mirrorRepository.upsert("annas-archive.gd", "https://annas-archive.gd", true, 150)

        val all = mirrorRepository.findAll()
        assertEquals(1, all.size)
        val mirror = all.first()
        assertEquals("annas-archive.gd", mirror.domain)
        assertEquals("https://annas-archive.gd", mirror.baseUrl)
        assertEquals(1, mirror.isWorking)
        assertEquals(150, mirror.responseTimeMs)
    }

    @Test
    fun upsertUpdatesExistingMirrorOnConflict() {
        mirrorRepository.upsert("annas-archive.gd", "https://annas-archive.gd", true, 300)
        mirrorRepository.upsert("annas-archive.gd", "https://annas-archive.gd", false, 999)

        val all = mirrorRepository.findAll()
        assertEquals(1, all.size)
        assertEquals(0, all.first().isWorking)
        assertEquals(999, all.first().responseTimeMs)
    }

    @Test
    fun findBestWorkingReturnsFastestWorkingMirror() {
        mirrorRepository.upsert("annas-archive.gd", "https://annas-archive.gd", true, 300)
        mirrorRepository.upsert("annas-archive.pk", "https://annas-archive.pk", true, 100)
        mirrorRepository.upsert("annas-archive.gl", "https://annas-archive.gl", true, 200)

        val best = mirrorRepository.findBestWorking()

        assertNotNull(best)
        assertEquals("https://annas-archive.pk", best!!.baseUrl)
    }

    @Test
    fun findBestWorkingReturnsNullWhenNoWorkingMirrors() {
        mirrorRepository.upsert("annas-archive.gd", "https://annas-archive.gd", false, 300)
        mirrorRepository.upsert("annas-archive.pk", "https://annas-archive.pk", false, 100)

        val best = mirrorRepository.findBestWorking()

        assertNull(best)
    }

    @Test
    fun getActiveMirrorReturnsCachedMirrorFromDb() {
        mirrorRepository.upsert("annas-archive.pk", "https://annas-archive.pk", true, 80)

        val baseUrl = mirrorService.getActiveMirror()

        assertEquals("https://annas-archive.pk", baseUrl)
    }

    @Test
    fun getActiveMirrorReturnsNullWhenNoMirrorsAvailable() {
        val baseUrl = mirrorService.getActiveMirror()

        assertNull(baseUrl)
    }

    @Test
    fun checkAllDomainsMarksUnreachableDomainsAsNotWorking() {
        coEvery { httpClient.fetch("https://annas-archive.gd") } returns HttpResult(
            body = "<html><title>Anna's Archive</title></html>",
            statusCode = 200
        )
        coEvery { httpClient.fetch("https://annas-archive.pk") } throws RuntimeException("Connection refused")
        coEvery { httpClient.fetch("https://annas-archive.gl") } returns HttpResult(
            body = "DDoS protection by Cloudflare",
            statusCode = 503
        )

        kotlinx.coroutines.runBlocking { mirrorService.checkAllDomains() }

        val all = mirrorRepository.findAll()
        val gdMirror = all.first { it.domain == "annas-archive.gd" }
        val pkMirror = all.first { it.domain == "annas-archive.pk" }
        val glMirror = all.first { it.domain == "annas-archive.gl" }

        assertEquals(1, gdMirror.isWorking)
        assertEquals(0, pkMirror.isWorking)
        assertEquals(0, glMirror.isWorking)
    }
}
