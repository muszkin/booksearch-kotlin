package pl.fairydeck.booksearch.service

import io.documentnode.epub4j.epub.EpubReader
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path

class MetadataExtractionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class BookMetadata(
    val title: String,
    val author: String,
    val description: String,
    val coverBytes: ByteArray?,
    val language: String,
    val publisher: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BookMetadata) return false
        return title == other.title &&
                author == other.author &&
                description == other.description &&
                coverBytes.contentEquals(other.coverBytes) &&
                language == other.language &&
                publisher == other.publisher
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + author.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + (coverBytes?.contentHashCode() ?: 0)
        result = 31 * result + language.hashCode()
        result = 31 * result + publisher.hashCode()
        return result
    }
}

class MetadataService {

    private val logger = LoggerFactory.getLogger(MetadataService::class.java)

    fun extractMetadata(filePath: Path): BookMetadata {
        val file = filePath.toFile()
        if (!file.exists()) {
            throw MetadataExtractionException("File not found: $filePath")
        }

        try {
            val book = FileInputStream(file).use { inputStream ->
                EpubReader().readEpub(inputStream)
            }

            val metadata = book.metadata
            val title = metadata.firstTitle ?: ""
            val author = metadata.authors.joinToString(", ") { formatAuthorName(it.firstname, it.lastname) }
            val description = metadata.descriptions.firstOrNull() ?: ""
            val language = metadata.language ?: ""
            val publisher = metadata.publishers.firstOrNull() ?: ""
            val coverBytes = readCoverBytes(book)

            logger.debug("Extracted metadata from {}: title='{}', author='{}'", filePath, title, author)

            return BookMetadata(
                title = title,
                author = author,
                description = description,
                coverBytes = coverBytes,
                language = language,
                publisher = publisher
            )
        } catch (e: MetadataExtractionException) {
            throw e
        } catch (e: Exception) {
            throw MetadataExtractionException("Failed to extract metadata from $filePath: ${e.message}", e)
        }
    }

    fun saveCoverImage(coverBytes: ByteArray, directory: File, md5: String): String {
        directory.mkdirs()
        val coverFile = File(directory, "${md5}_cover.jpg")
        coverFile.writeBytes(coverBytes)
        logger.debug("Saved cover image to {}", coverFile.absolutePath)
        return coverFile.absolutePath
    }

    private fun readCoverBytes(book: io.documentnode.epub4j.domain.Book): ByteArray? {
        val coverImage = book.coverImage ?: return null
        return try {
            coverImage.data
        } catch (e: Exception) {
            logger.warn("Failed to read cover image data: {}", e.message)
            null
        }
    }

    private fun formatAuthorName(firstname: String?, lastname: String?): String {
        val parts = listOfNotNull(
            firstname?.takeIf { it.isNotBlank() },
            lastname?.takeIf { it.isNotBlank() }
        )
        return parts.joinToString(" ")
    }
}
