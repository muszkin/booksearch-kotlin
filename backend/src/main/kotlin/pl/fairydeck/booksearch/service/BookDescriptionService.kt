package pl.fairydeck.booksearch.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.infrastructure.AnnaArchiveRecordClient
import pl.fairydeck.booksearch.infrastructure.OpenRouterClient
import pl.fairydeck.booksearch.repository.BookRepository

/**
 * Resolves a book description, preferring the publisher blurb Anna's Archive holds and
 * falling back to a generated one. The source travels with the text so the interface can
 * tell a reader which of the two they are looking at.
 */
class BookDescriptionService(
    private val bookRepository: BookRepository,
    private val recordClient: AnnaArchiveRecordClient,
    private val openRouterClient: OpenRouterClient,
    private val mirrorService: MirrorService
) {

    private val logger = LoggerFactory.getLogger(BookDescriptionService::class.java)

    val canGenerate: Boolean get() = openRouterClient.isConfigured
    private val enrichmentScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Resolves the description off the request path, so adding a book to the library does
     * not wait on a lookup that can take ten seconds.
     */
    fun enrichInBackground(bookMd5: String) {
        enrichmentScope.launch {
            try {
                describe(bookMd5)
            } catch (e: Exception) {
                logger.info("Background description lookup failed for {}: {}", bookMd5, e.message)
            }
        }
    }

    suspend fun describe(bookMd5: String): BookDescription? {
        val book = bookRepository.findByMd5(bookMd5) ?: return null

        val stored = book.description?.takeIf { it.isNotBlank() }
        if (stored != null) {
            return BookDescription(stored, book.descriptionSource ?: SOURCE_ARCHIVE, book.isbn)
        }
        // A lookup that already came back empty is not worth repeating on every expand.
        if (book.descriptionCheckedAt != null) return null

        val record = mirrorService.getDownloadMirrors()
            .firstNotNullOfOrNull { mirror -> recordClient.fetch(bookMd5, mirror) }

        val blurb = record?.description?.takeIf { it.length >= MIN_USEFUL_LENGTH }
        if (blurb != null) {
            bookRepository.saveDescription(bookMd5, blurb, SOURCE_ARCHIVE, record.isbn)
            return BookDescription(blurb, SOURCE_ARCHIVE, record.isbn)
        }

        val generated = generateDescription(book.title.orEmpty(), book.author.orEmpty())
        if (generated != null) {
            bookRepository.saveDescription(bookMd5, generated, SOURCE_GENERATED, record?.isbn)
            return BookDescription(generated, SOURCE_GENERATED, record?.isbn)
        }

        record?.isbn?.let { bookRepository.saveIsbn(bookMd5, it) }
        bookRepository.markDescriptionChecked(bookMd5)
        logger.info("No description available for {}", bookMd5)
        return null
    }

    /**
     * Replaces whatever is stored with a freshly generated description. Used when the
     * archive's blurb is wrong for the book; the archive is not consulted again, and a
     * decline from the model leaves the stored text untouched rather than blanking it.
     */
    suspend fun regenerate(bookMd5: String): BookDescription? {
        val book = bookRepository.findByMd5(bookMd5) ?: return null

        val generated = generateDescription(book.title.orEmpty(), book.author.orEmpty())
            ?: return null

        bookRepository.saveDescription(bookMd5, generated, SOURCE_GENERATED, isbn = null)
        logger.info("Regenerated the description for {} on request", bookMd5)
        return BookDescription(generated, SOURCE_GENERATED, book.isbn)
    }

    private suspend fun generateDescription(title: String, author: String): String? {
        if (!openRouterClient.isConfigured || title.isBlank()) return null
        return openRouterClient.describeBook(title, author)
    }

    private companion object {
        const val SOURCE_ARCHIVE = "annas-archive"
        const val SOURCE_GENERATED = "openrouter"

        /** Some records carry a genre tag in place of a blurb, e.g. "Fantasy,Sci-Fi". */
        const val MIN_USEFUL_LENGTH = 60
    }
}

data class BookDescription(
    val description: String,
    val source: String,
    val isbn: String?
)
