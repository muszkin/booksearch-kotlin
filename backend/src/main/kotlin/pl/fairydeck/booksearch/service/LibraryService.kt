package pl.fairydeck.booksearch.service

import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.ConflictException
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.infrastructure.ScraperConfig
import pl.fairydeck.booksearch.repository.BookRepository
import pl.fairydeck.booksearch.repository.LibraryEntryWithBook
import pl.fairydeck.booksearch.repository.UserLibraryRepository
import java.io.File

class LibraryService(
    private val userLibraryRepository: UserLibraryRepository,
    private val bookRepository: BookRepository,
    private val scraperConfig: ScraperConfig
) {

    private val logger = LoggerFactory.getLogger(LibraryService::class.java)

    fun addToLibrary(userId: Int, bookMd5: String, format: String): LibraryBook {
        val book = bookRepository.findByMd5(bookMd5)
            ?: throw NotFoundException("Book not found with md5: $bookMd5")

        val record = userLibraryRepository.add(userId, bookMd5, format)
        logger.info("User {} added book {} ({}) to library", userId, bookMd5, format)

        return LibraryBook(
            id = record.id!!,
            bookMd5 = bookMd5,
            format = format,
            filePath = record.filePath,
            addedAt = record.addedAt!!,
            title = book.title ?: "",
            author = book.author ?: "",
            language = book.language ?: "",
            fileSize = book.fileSize ?: "",
            detailUrl = book.detailUrl ?: "",
            coverUrl = book.coverUrl ?: "",
            publisher = book.publisher ?: "",
            year = book.year ?: "",
            description = book.description ?: ""
        )
    }

    fun getUserLibrary(userId: Int, page: Int, pageSize: Int): LibraryListResponse {
        val totalItems = userLibraryRepository.countByUserId(userId)
        val entries = userLibraryRepository.findByUserId(userId, page, pageSize)

        return LibraryListResponse(
            items = entries.map { it.toLibraryBook() },
            totalItems = totalItems,
            page = page,
            pageSize = pageSize,
            totalPages = ((totalItems + pageSize - 1) / pageSize).toInt()
        )
    }

    fun removeFromLibrary(userId: Int, entryId: Int) {
        val deleted = userLibraryRepository.deleteById(entryId, userId)
        if (!deleted) {
            throw NotFoundException("Library entry not found")
        }
        logger.info("User {} removed library entry {}", userId, entryId)
    }

    fun getFileForEntry(userId: Int, entryId: Int): LibraryFileInfo {
        val entry = userLibraryRepository.findByIdAndUserId(entryId, userId)
            ?: throw NotFoundException("Library entry not found")

        val filePath = entry.filePath
            ?: throw NotFoundException("File not yet downloaded for this library entry")

        val absolutePath = File(scraperConfig.dataPath, filePath).absolutePath
        val file = File(absolutePath)
        if (!file.exists()) {
            throw NotFoundException("File not found on disk")
        }

        val book = bookRepository.findByMd5(entry.bookMd5!!)
        val title = book?.title?.takeIf { it.isNotBlank() } ?: entry.bookMd5!!
        val format = entry.format ?: "epub"

        return LibraryFileInfo(
            absolutePath = absolutePath,
            title = title,
            format = format
        )
    }

    fun checkOwnership(userId: Int, md5s: List<String>): Map<String, OwnershipResult> {
        val libraryEntries = userLibraryRepository.findByUserAndBookMd5s(userId, md5s)
        val ownedByMd5 = libraryEntries.groupBy { it.bookMd5 ?: "" }

        return md5s.associateWith { md5 ->
            val owned = ownedByMd5[md5]
            if (owned != null) {
                OwnershipResult(
                    matchType = "exact",
                    ownedFormats = owned.mapNotNull { it.format }
                )
            } else {
                OwnershipResult(matchType = "none", ownedFormats = emptyList())
            }
        }
    }

    private fun LibraryEntryWithBook.toLibraryBook(): LibraryBook = LibraryBook(
        id = id,
        bookMd5 = bookMd5,
        format = format,
        filePath = filePath,
        addedAt = addedAt,
        title = title,
        author = author,
        language = language,
        fileSize = fileSize,
        detailUrl = detailUrl,
        coverUrl = coverUrl,
        publisher = publisher,
        year = year,
        description = description
    )
}

@Serializable
data class LibraryBook(
    val id: Int,
    val bookMd5: String,
    val format: String,
    val filePath: String?,
    val addedAt: String,
    val title: String,
    val author: String,
    val language: String,
    val fileSize: String,
    val detailUrl: String,
    val coverUrl: String,
    val publisher: String,
    val year: String,
    val description: String
)

@Serializable
data class LibraryListResponse(
    val items: List<LibraryBook>,
    val totalItems: Long,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int
)

@Serializable
data class OwnershipResult(
    val matchType: String,
    val ownedFormats: List<String>
)

@Serializable
data class AddToLibraryRequest(
    val bookMd5: String,
    val format: String
)

data class LibraryFileInfo(
    val absolutePath: String,
    val title: String,
    val format: String
)
