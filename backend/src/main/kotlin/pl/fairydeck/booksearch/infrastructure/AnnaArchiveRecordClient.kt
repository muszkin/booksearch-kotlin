package pl.fairydeck.booksearch.infrastructure

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Reads Anna's Archive's per-record metadata, which carries the publisher blurb and
 * ISBNs that the search listing leaves out. The endpoint answers 403 to anonymous
 * callers, so it goes through the member session.
 */
class AnnaArchiveRecordClient(private val sessionClient: AnnaArchiveSessionClient) {

    private val logger = LoggerFactory.getLogger(AnnaArchiveRecordClient::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(bookMd5: String, mirror: String): BookRecord? {
        val url = "$mirror/db/aarecord_elasticsearch/md5:$bookMd5.json"

        val body = try {
            sessionClient.fetchPage(url)
        } catch (e: Exception) {
            logger.info("Could not read the Anna's Archive record for {}: {}", bookMd5, e.message)
            return null
        }

        val record = try {
            json.decodeFromString<RecordResponse>(body)
        } catch (e: Exception) {
            logger.info("Unreadable Anna's Archive record for {}: {}", bookMd5, e.message)
            return null
        }

        val data = record.fileUnifiedData ?: return BookRecord(null, null)
        val identifiers = data.identifiersUnified

        return BookRecord(
            description = data.strippedDescriptionBest?.trim()?.takeIf { it.isNotEmpty() },
            isbn = identifiers?.isbn13?.firstOrNull() ?: identifiers?.isbn10?.firstOrNull()
        )
    }
}

data class BookRecord(
    val description: String?,
    val isbn: String?
)

@Serializable
private data class RecordResponse(
    @SerialName("file_unified_data")
    val fileUnifiedData: FileUnifiedData? = null
)

@Serializable
private data class FileUnifiedData(
    @SerialName("stripped_description_best")
    val strippedDescriptionBest: String? = null,
    @SerialName("identifiers_unified")
    val identifiersUnified: IdentifiersUnified? = null
)

@Serializable
private data class IdentifiersUnified(
    val isbn13: List<String> = emptyList(),
    val isbn10: List<String> = emptyList()
)
