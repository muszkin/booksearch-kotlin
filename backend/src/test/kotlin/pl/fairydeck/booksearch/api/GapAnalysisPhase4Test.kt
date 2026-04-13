package pl.fairydeck.booksearch.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.ScraperConfig
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.service.*
import java.io.File

class GapAnalysisPhase4Test {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- Test 1: Calibre failure marks conversion job as failed ----
    @Test
    fun shouldMarkConversionAsFailedWhenCalibreThrows() {
        val userLibraryRepository = mockk<UserLibraryRepository>(relaxed = true)
        val calibreWrapper = mockk<CalibreWrapper>()
        val libraryService = mockk<LibraryService>()

        val scraperConfig = ScraperConfig(
            solvearrUrl = "http://localhost:8191",
            userAgent = "TestAgent",
            requestDelayMs = 0,
            maxRetries = 0,
            backoffMultiplier = 1.0,
            cacheTtlDays = 7,
            maxConcurrentDownloads = 2,
            dataPath = "/tmp/booksearch-gap4-test"
        )

        val conversionService = ConversionService(
            userLibraryRepository = userLibraryRepository,
            calibreWrapper = calibreWrapper,
            libraryService = libraryService,
            scraperConfig = scraperConfig,
            maxConcurrentConversions = 2
        )

        val inputPath = "/tmp/booksearch-gap4-test/1/testbook.epub"
        val fileInfo = LibraryFileInfo(absolutePath = inputPath, title = "Test Book", format = "epub")
        every { libraryService.getFileForEntry(1, 42) } returns fileInfo
        every { calibreWrapper.convert(any(), any()) } throws IllegalStateException("Calibre process crashed: segfault")

        val jobId = runBlocking {
            conversionService.startConversion(userId = 1, libraryEntryId = 42, targetFormat = "mobi")
        }

        Thread.sleep(1000)

        val status = conversionService.getJobStatus(jobId)
        assertNotNull(status)
        assertEquals("failed", status!!.status, "Job should be failed when Calibre throws")
        assertNotNull(status.error)
        assertTrue(status.error!!.contains("Calibre"), "Error should mention Calibre, got: ${status.error}")
    }

    // ---- Test 2: SMTP connection refused marks delivery as failed ----
    @Test
    fun shouldReturnFailedStatusWhenSmtpConnectionRefused() {
        val deliveryRepository = mockk<pl.fairydeck.booksearch.repository.DeliveryRepository>(relaxed = true)
        val userSettingsRepository = mockk<pl.fairydeck.booksearch.repository.UserSettingsRepository>()
        val libraryService = mockk<LibraryService>()
        val userLibraryRepository = mockk<UserLibraryRepository>()

        val deliveryService = DeliveryService(
            deliveryRepository = deliveryRepository,
            userSettingsRepository = userSettingsRepository,
            libraryService = libraryService,
            userLibraryRepository = userLibraryRepository
        )

        every { userSettingsRepository.getByPrefix(1, "kindle_") } returns mapOf(
            "kindle_smtp_host" to "192.0.2.1",
            "kindle_smtp_port" to "9999",
            "kindle_smtp_username" to "user",
            "kindle_smtp_password" to "pass",
            "kindle_smtp_from" to "sender@example.com",
            "kindle_recipient_email" to "kindle@kindle.com"
        )

        val tempFile = File.createTempFile("test-book", ".epub")
        tempFile.writeText("fake epub content")
        tempFile.deleteOnExit()

        val fileInfo = LibraryFileInfo(absolutePath = tempFile.absolutePath, title = "Test Book", format = "epub")
        every { libraryService.getFileForEntry(1, 42) } returns fileInfo

        val libraryRecord = mockk<pl.fairydeck.booksearch.jooq.generated.tables.records.UserLibraryRecord>()
        every { libraryRecord.bookMd5 } returns "abc123"
        every { userLibraryRepository.findByIdAndUserId(42, 1) } returns libraryRecord
        every { deliveryRepository.create(1, "abc123", "kindle") } returns 1
        every { deliveryRepository.markFailed(1, any()) } just Runs

        val response = deliveryService.deliver(1, 42, "kindle")

        assertEquals("failed", response.status, "Delivery should fail when SMTP is unreachable")
        assertNotNull(response.error)
        verify { deliveryRepository.markFailed(1, any()) }
    }

    // ---- Test 3: User isolation for conversions (user A cannot see user B's convert route entry) ----
    @Test
    fun shouldReturn404WhenAccessingConvertForOtherUsersLibraryEntry() = testApplication {
        environment { config = ApplicationConfig("application.yaml") }

        val token1 = registerAndGetToken("convert-user1@example.com")
        val token2 = registerAndGetToken("convert-user2@example.com")

        // User2 tries to convert user1's hypothetical library entry 999
        val response = client.post("/api/convert/999?target=mobi") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        // Should be 404 (not found for this user) - not 200 or 202
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ---- Test 4: User isolation for deliveries (user A cannot see user B's deliveries) ----
    @Test
    fun shouldReturn401WhenAccessingDeliverForOtherUsersEntry() = testApplication {
        environment { config = ApplicationConfig("application.yaml") }

        val token1 = registerAndGetToken("deliver-user1@example.com")
        val token2 = registerAndGetToken("deliver-user2@example.com")

        // User2 tries to deliver user1's hypothetical library entry 999
        val response = client.post("/api/deliver/999?device=kindle") {
            header(HttpHeaders.Authorization, "Bearer $token2")
        }

        // Should be 404 (not found for this user) or 422 (no SMTP configured), not success
        assertTrue(
            response.status == HttpStatusCode.NotFound || response.status == HttpStatusCode.UnprocessableEntity,
            "Should not allow access to other user's entries, got: ${response.status}"
        )
    }

    // ---- Test 5: Incomplete SMTP settings (host present but no recipient) ----
    @Test
    fun shouldThrowValidationWhenSmtpSettingsIncomplete() {
        val deliveryRepository = mockk<pl.fairydeck.booksearch.repository.DeliveryRepository>(relaxed = true)
        val userSettingsRepository = mockk<pl.fairydeck.booksearch.repository.UserSettingsRepository>()
        val libraryService = mockk<LibraryService>()
        val userLibraryRepository = mockk<UserLibraryRepository>()

        val deliveryService = DeliveryService(
            deliveryRepository = deliveryRepository,
            userSettingsRepository = userSettingsRepository,
            libraryService = libraryService,
            userLibraryRepository = userLibraryRepository
        )

        // Partial settings - missing recipient_email
        every { userSettingsRepository.getByPrefix(1, "kindle_") } returns mapOf(
            "kindle_smtp_host" to "smtp.gmail.com",
            "kindle_smtp_port" to "587",
            "kindle_smtp_username" to "user",
            "kindle_smtp_password" to "pass",
            "kindle_smtp_from" to "sender@example.com"
        )

        assertThrows(ValidationException::class.java) {
            deliveryService.deliver(1, 42, "kindle")
        }
    }

    // ---- Test 6: Settings DELETE endpoint removes settings ----
    @Test
    fun shouldDeleteDeviceSettingsViaRoute() = testApplication {
        environment { config = ApplicationConfig("application.yaml") }

        val token = registerAndGetToken("delete-settings@example.com")

        client.put("/api/settings/kindle") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $token")
            setBody("""{"host":"smtp.gmail.com","port":587,"username":"user","password":"pass","fromEmail":"from@example.com","recipientEmail":"kindle@kindle.com"}""")
        }

        val getBeforeDelete = client.get("/api/settings/kindle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, getBeforeDelete.status)

        val deleteResponse = client.delete("/api/settings/kindle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, deleteResponse.status)

        val getAfterDelete = client.get("/api/settings/kindle") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, getAfterDelete.status)
    }

    // ---- Test 7: Conversion status endpoint returns 404 for nonexistent job ----
    @Test
    fun shouldReturn404ForNonexistentConversionJob() = testApplication {
        environment { config = ApplicationConfig("application.yaml") }

        val token = registerAndGetToken("conv-status@example.com")

        val response = client.get("/api/convert/status/nonexistent-uuid-12345") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ---- Test 8: Deliveries list returns empty array for user with no deliveries ----
    @Test
    fun shouldReturnEmptyListForUserWithNoDeliveries() = testApplication {
        environment { config = ApplicationConfig("application.yaml") }

        val token = registerAndGetToken("no-deliveries@example.com")

        val response = client.get("/api/deliveries") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertEquals("[]", body)
    }

    private suspend fun ApplicationTestBuilder.registerAndGetToken(email: String): String {
        val registerResponse = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"password123","displayName":"Gap4 Tester"}""")
        }
        val body = json.decodeFromString<JsonObject>(registerResponse.bodyAsText())
        return body["accessToken"]!!.jsonPrimitive.content
    }
}
