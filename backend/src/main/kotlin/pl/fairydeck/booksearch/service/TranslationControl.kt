package pl.fairydeck.booksearch.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

@Serializable
data class TranslationOptions(
    val modelId: String? = null,
    val autoFallback: Boolean = true,
    val fallbackModelIds: List<String> = emptyList(),
    val referenceLibraryIds: List<Int> = emptyList(),
    val referenceChapters: Int = 3,
    val glossary: String = "",
    val notes: String = "",
    val referenceText: String = ""
) {
    fun promptContext() = "Glossary:\n$glossary\nContext notes:\n$notes\nPolish reference excerpts:\n$referenceText"
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class TranslationAttempt(
    @EncodeDefault val id: String = java.util.UUID.randomUUID().toString(),
    @EncodeDefault val startedAt: String = java.time.Instant.now().toString(),
    val chapterIndex: Int,
    val segmentIndex: Int,
    val requestedModel: String,
    val actualModel: String? = null,
    @EncodeDefault val status: String = "running",
    val errorCode: String? = null,
    val message: String? = null,
    @EncodeDefault val inputTokens: Int = 0,
    @EncodeDefault val outputTokens: Int = 0,
    @EncodeDefault val itemCount: Int = 0,
    val finishReason: String? = null
)

@Serializable
data class TranslationChapterDetail(val index: Int, val href: String, val status: String, val attempts: Int, val error: String?, val translatedSegments: Int, val totalSegments: Int)

@Serializable
data class TranslationDetails(val options: TranslationOptions, val chapters: List<TranslationChapterDetail>, val attempts: List<TranslationAttempt>)
