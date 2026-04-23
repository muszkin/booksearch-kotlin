package pl.fairydeck.booksearch.service

import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.Authenticator
import jakarta.mail.Part
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.repository.DeliveryRepository
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import pl.fairydeck.booksearch.repository.UserSettingsRepository
import java.io.File
import java.util.Properties

class DeliveryService(
    private val deliveryRepository: DeliveryRepository,
    private val userSettingsRepository: UserSettingsRepository,
    private val libraryService: LibraryService,
    private val userLibraryRepository: UserLibraryRepository
) {

    private val logger = LoggerFactory.getLogger(DeliveryService::class.java)

    fun deliver(userId: Int, libraryEntryId: Int, device: String): DeliveryResponse {
        validateDevice(device)

        val smtpConfig = loadSmtpConfig(userId, device)
        val fileInfo = libraryService.getFileForEntry(userId, libraryEntryId)
        val file = File(fileInfo.absolutePath)

        val libraryEntry = userLibraryRepository.findByIdAndUserId(libraryEntryId, userId)
            ?: throw NotFoundException("Library entry not found")
        val bookMd5 = libraryEntry.bookMd5!!

        val deliveryId = deliveryRepository.create(userId, bookMd5, device)

        try {
            sendEmail(smtpConfig, file, fileInfo.title, fileInfo.format)
            deliveryRepository.markSent(deliveryId)
            logger.info("Delivery {} sent to {} for user {}", deliveryId, device, userId)
            return DeliveryResponse(deliveryId = deliveryId, status = "sent")
        } catch (e: Exception) {
            val errorMessage = e.message ?: "Unknown error"
            deliveryRepository.markFailed(deliveryId, errorMessage)
            logger.error("Delivery {} failed for user {}: {}", deliveryId, userId, errorMessage, e)
            return DeliveryResponse(deliveryId = deliveryId, status = "failed", error = errorMessage)
        }
    }

    fun getDeliveriesForUser(userId: Int): List<DeliveryRecord> =
        deliveryRepository.findByUser(userId)

    fun getDeliveriesForBook(userId: Int, bookMd5: String): List<DeliveryRecord> =
        deliveryRepository.findByUserAndBook(userId, bookMd5)

    private fun validateDevice(device: String) {
        if (device !in ALLOWED_DEVICES) {
            throw ValidationException("Device must be one of: ${ALLOWED_DEVICES.joinToString(", ")}")
        }
    }

    private fun loadSmtpConfig(userId: Int, device: String): SmtpConfig {
        val prefix = "${device}_"
        val settings = userSettingsRepository.getByPrefix(userId, prefix)

        if (settings.isEmpty()) {
            throw ValidationException("SMTP settings not configured for $device. Configure them in Settings first.")
        }

        val host = settings["${prefix}smtp_host"]
            ?: throw ValidationException("SMTP host not configured for $device")
        val port = settings["${prefix}smtp_port"]?.toIntOrNull()
            ?: throw ValidationException("SMTP port not configured for $device")
        val username = settings["${prefix}smtp_username"] ?: ""
        val password = settings["${prefix}smtp_password"] ?: ""
        val fromEmail = settings["${prefix}smtp_from"]
            ?: throw ValidationException("Sender email not configured for $device")
        val recipientEmail = settings["${prefix}recipient_email"]
            ?: throw ValidationException("Recipient email not configured for $device")

        return SmtpConfig(
            host = host,
            port = port,
            username = username,
            password = password,
            fromEmail = fromEmail,
            recipientEmail = recipientEmail
        )
    }

    private fun sendEmail(config: SmtpConfig, file: File, bookTitle: String, format: String) {
        require(file.exists()) { "Attachment file does not exist: ${file.absolutePath}" }
        require(file.length() > 0) { "Attachment file is empty: ${file.absolutePath}" }

        val properties = Properties().apply {
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())
            put("mail.smtp.auth", (config.username.isNotBlank()).toString())
            put("mail.smtp.starttls.enable", (config.port != MAILPIT_PORT).toString())
            // RFC 2231 encoding — survives non-ASCII filenames and picky receivers (PocketBook relay).
            put("mail.mime.encodefilename", "true")
            put("mail.mime.encodeparameters", "true")
        }

        val session = if (config.username.isNotBlank()) {
            Session.getInstance(properties, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(config.username, config.password)
            })
        } else {
            Session.getInstance(properties)
        }

        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(config.fromEmail))
            setRecipient(MimeMessage.RecipientType.TO, InternetAddress(config.recipientEmail))
            subject = bookTitle
        }

        val multipart = MimeMultipart("mixed")

        val textPart = MimeBodyPart().apply {
            setText("Sending book: $bookTitle", StandardCharsets.UTF_8.name())
        }
        multipart.addBodyPart(textPart)

        // Explicit MIME headers so even the pickiest parser (PocketBook relay) recognises the attachment.
        // Earlier attempts using attachFile(file, mime, encoding) did not reliably emit the
        // "Content-Disposition: attachment" header in this Jakarta Mail version — hence the bounce
        // "message contains no attachments". Setting every header manually removes the guesswork.
        val mime = mimeTypeForFormat(format)
        val safeFileName = sanitizeFileName("$bookTitle.$format")
        val encodedFileName = MimeUtility.encodeText(safeFileName, StandardCharsets.UTF_8.name(), "B")
        val attachmentPart = MimeBodyPart().apply {
            dataHandler = DataHandler(FileDataSource(file))
            setHeader("Content-Type", "$mime; name=\"$encodedFileName\"")
            setHeader("Content-Transfer-Encoding", "base64")
            setDisposition(Part.ATTACHMENT)
            fileName = safeFileName
        }
        multipart.addBodyPart(attachmentPart)

        message.setContent(multipart)
        message.saveChanges()

        logger.info(
            "Delivery outbound email: recipient={} from={} fileName={} fileSize={} mime={}",
            config.recipientEmail, config.fromEmail, safeFileName, file.length(), mime
        )
        val debugPath = dumpRawMessage(message, config.recipientEmail)
        if (debugPath != null) {
            logger.info("Delivery raw .eml dump: {}", debugPath)
        }

        Transport.send(message)
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").take(200)

    private fun dumpRawMessage(message: MimeMessage, recipient: String): Path? = try {
        val debugDir = Path.of("/app/data/delivery-debug")
        Files.createDirectories(debugDir)
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(
            Instant.now().atZone(java.time.ZoneOffset.UTC)
        )
        val safeRecipient = recipient.replace(Regex("[^a-zA-Z0-9@._-]"), "_")
        val target = debugDir.resolve("$ts-$safeRecipient.eml")
        ByteArrayOutputStream().use { baos ->
            message.writeTo(baos)
            Files.write(target, baos.toByteArray())
        }
        target
    } catch (e: Exception) {
        logger.warn("Could not dump raw email for diagnostics: {}", e.message)
        null
    }

    companion object {
        private val ALLOWED_DEVICES = setOf("kindle", "pocketbook")
        private const val MAILPIT_PORT = 1025

        fun mimeTypeForFormat(format: String): String = when (format.lowercase()) {
            "epub" -> "application/epub+zip"
            "mobi" -> "application/x-mobipocket-ebook"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val fromEmail: String,
    val recipientEmail: String
)

@Serializable
data class DeliveryResponse(
    val deliveryId: Int,
    val status: String,
    val error: String? = null
)

@Serializable
data class DeliveryRecord(
    val id: Int,
    val userId: Int,
    val bookMd5: String,
    val deviceType: String,
    val status: String,
    val sentAt: String?,
    val error: String?,
    val createdAt: String
)
