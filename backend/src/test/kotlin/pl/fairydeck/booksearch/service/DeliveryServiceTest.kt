package pl.fairydeck.booksearch.service

import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.jooq.generated.tables.records.UserLibraryRecord
import pl.fairydeck.booksearch.repository.DeliveryRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.repository.UserSettingsRepository

class DeliveryServiceTest {

    private lateinit var deliveryRepository: DeliveryRepository
    private lateinit var userSettingsRepository: UserSettingsRepository
    private lateinit var libraryService: LibraryService
    private lateinit var userLibraryRepository: UserLibraryRepository
    private lateinit var deliveryService: DeliveryService

    @BeforeEach
    fun setUp() {
        deliveryRepository = mockk(relaxed = true)
        userSettingsRepository = mockk()
        libraryService = mockk()
        userLibraryRepository = mockk()

        deliveryService = DeliveryService(
            deliveryRepository = deliveryRepository,
            userSettingsRepository = userSettingsRepository,
            libraryService = libraryService,
            userLibraryRepository = userLibraryRepository
        )
    }

    @Test
    fun shouldCreateDeliveryRecordWhenSmtpConfigured() {
        val userId = 1
        val libraryEntryId = 42
        val device = "kindle"

        every { userSettingsRepository.getByPrefix(userId, "kindle_") } returns mapOf(
            "kindle_smtp_host" to "localhost",
            "kindle_smtp_port" to "1025",
            "kindle_smtp_username" to "",
            "kindle_smtp_password" to "",
            "kindle_smtp_from" to "test@example.com",
            "kindle_recipient_email" to "kindle@kindle.com"
        )

        val fileInfo = LibraryFileInfo(
            absolutePath = "/tmp/test-book.epub",
            title = "Test Book",
            format = "epub"
        )
        every { libraryService.getFileForEntry(userId, libraryEntryId) } returns fileInfo

        val libraryRecord = mockk<UserLibraryRecord>()
        every { libraryRecord.bookMd5 } returns "abc123"
        every { userLibraryRepository.findByIdAndUserId(libraryEntryId, userId) } returns libraryRecord
        every { deliveryRepository.create(userId, "abc123", device) } returns 1

        val result = deliveryService.deliver(userId, libraryEntryId, device)

        assertNotNull(result)
        assertEquals(1, result.deliveryId)
        verify { deliveryRepository.create(userId, "abc123", device) }
    }

    @Test
    fun shouldThrowValidationExceptionWhenSmtpNotConfigured() {
        val userId = 1
        val libraryEntryId = 42
        val device = "kindle"

        every { userSettingsRepository.getByPrefix(userId, "kindle_") } returns emptyMap()

        assertThrows(ValidationException::class.java) {
            deliveryService.deliver(userId, libraryEntryId, device)
        }
    }

    @Test
    fun shouldThrowValidationExceptionForInvalidDevice() {
        val userId = 1
        val libraryEntryId = 42

        assertThrows(ValidationException::class.java) {
            deliveryService.deliver(userId, libraryEntryId, "tablet")
        }
    }

    @Test
    fun shouldResolveCorrectMimeTypeForEpub() {
        assertEquals("application/epub+zip", DeliveryService.mimeTypeForFormat("epub"))
    }

    @Test
    fun shouldResolveCorrectMimeTypeForMobi() {
        assertEquals("application/x-mobipocket-ebook", DeliveryService.mimeTypeForFormat("mobi"))
    }

    @Test
    fun shouldTrackDeliveriesByUserAndBook() {
        val userId = 1
        val bookMd5 = "abc123"

        val records = listOf(
            DeliveryRecord(id = 1, userId = userId, bookMd5 = bookMd5, deviceType = "kindle", status = "sent", sentAt = "2026-04-11T10:00:00Z", error = null, createdAt = "2026-04-11T09:59:00Z"),
            DeliveryRecord(id = 2, userId = userId, bookMd5 = bookMd5, deviceType = "pocketbook", status = "failed", sentAt = null, error = "Connection refused", createdAt = "2026-04-11T10:05:00Z")
        )

        every { deliveryRepository.findByUserAndBook(userId, bookMd5) } returns records

        val deliveries = deliveryService.getDeliveriesForBook(userId, bookMd5)

        assertEquals(2, deliveries.size)
        assertEquals("sent", deliveries[0].status)
        assertEquals("failed", deliveries[1].status)
    }
}
