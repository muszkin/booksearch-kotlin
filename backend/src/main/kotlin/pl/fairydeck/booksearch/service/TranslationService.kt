package pl.fairydeck.booksearch.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.api.ConflictException
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.infrastructure.OpenRouterClient
import pl.fairydeck.booksearch.infrastructure.OpenRouterException
import pl.fairydeck.booksearch.repository.*
import java.io.IOException
import java.util.concurrent.TimeoutException

class TranslationService(
    private val jobs: TranslationJobRepository,
    private val chapters: TranslationChapterRepository,
    private val settings: SystemConfigRepository,
    private val library: LibraryService,
    private val workspace: EpubTranslationWorkspace,
    private val client: OpenRouterClient?,
    private val scope: CoroutineScope,
    private val retryDelayMillis: Long = 1000
) {
    private val requests = Mutex()
    private val transitions = Any()

    suspend fun estimate(userId: Int, sourceLibraryEntryId: Int): TranslationEstimate {
        val source = library.translationSource(userId, sourceLibraryEntryId)
        val model = settings.getTranslationDefaultModel() ?: throw ValidationException("Translation is not configured")
        validateModel(model)
        val plan = validPlan { workspace.inspect(source.file) }
        if (!isEnglish(plan.language)) throw ValidationException("Translation requires an English EPUB")
        return TranslationEstimate(plan.chapters.size, plan.estimatedInputTokens, model)
    }

    suspend fun start(userId: Int, sourceLibraryEntryId: Int, externalProcessingConfirmed: Boolean): TranslationStarted {
        if (!externalProcessingConfirmed) throw ValidationException("External processing confirmation is required")
        val estimate = estimate(userId, sourceLibraryEntryId)
        return synchronized(transitions) {
            if (jobs.findActiveBySourceLibraryEntryId(userId, sourceLibraryEntryId) != null) throw ConflictException("Translation already active for this EPUB")
            val source = library.translationSource(userId, sourceLibraryEntryId)
            val id = jobs.create(userId, sourceLibraryEntryId, estimate.modelId)
            try {
                val plan = workspace.create(source.file, id)
                jobs.updatePlan(id, source.bookMd5, source.file.absolutePath, plan.directory.toString(), plan.chapters.size, plan.estimatedInputTokens)
                plan.chapters.forEach { chapters.create(id, it.index, it.href) }
                enqueue(userId, id)
                TranslationStarted(id)
            } catch (_: Exception) {
                jobs.markFailed(id, "workspace_failed")
                throw ValidationException("Could not prepare EPUB translation")
            }
        }
    }

    fun status(userId: Int, jobId: String): TranslationStatus {
        val job = owned(userId, jobId)
        return TranslationStatus(jobId, job.status!!, job.sourceLibraryEntryId!!, job.modelId!!,
            job.totalChapters!!, job.completedChapters!!, job.failedChapterIndex, job.estimatedInputTokens!!,
            job.actualInputTokens!!, job.actualOutputTokens!!, job.outputLibraryEntryId, job.error, job.status == "paused")
    }

    suspend fun resume(userId: Int, jobId: String): TranslationStarted {
        val job = owned(userId, jobId)
        if (job.status != "paused") throw ConflictException("Only paused translations can resume")
        val source = library.translationSource(userId, job.sourceLibraryEntryId!!)
        if (source.bookMd5 != job.sourceBookMd5 || source.file.absolutePath != job.sourceFilePath) throw ValidationException("Translation source changed")
        val plan = validPlan { workspace.load(jobId) }
        if (java.nio.file.Files.mismatch(source.file.toPath(), plan.source.toPath()) != -1L) throw ValidationException("Translation source changed")
        validateModel(job.modelId!!)
        return synchronized(transitions) {
            if (owned(userId, jobId).status != "paused") throw ConflictException("Translation is no longer paused")
            jobs.queue(jobId)
            enqueue(userId, jobId)
            TranslationStarted(jobId)
        }
    }

    fun cancel(userId: Int, jobId: String) = synchronized(transitions) {
        val job = owned(userId, jobId)
        if (job.status !in listOf("queued", "paused")) throw ConflictException("Only queued or paused translations can be cancelled")
        jobs.cancel(jobId)
        workspace.delete(jobId)
    }

    private fun enqueue(userId: Int, jobId: String) {
        scope.launch {
            requests.withLock {
                synchronized(transitions) {
                    if (owned(userId, jobId).status != "queued") return@launch
                    jobs.markRunning(jobId)
                }
                try { execute(userId, jobId) }
                catch (e: CancellationException) { jobs.markPaused(jobId, "server_restart"); throw e }
                catch (_: Exception) { jobs.markPaused(jobId, "workspace_failed") }
            }
        }
    }

    private suspend fun execute(userId: Int, jobId: String) {
        val job = owned(userId, jobId)
        val plan = workspace.load(jobId)
        for (chapter in plan.chapters) {
            if (chapters.findByJobId(jobId).first { it.chapterIndex == chapter.index }.status == "completed") continue
            var complete = false
            for (attempt in 1..3) {
                chapters.markAttemptStarted(jobId, chapter.index)
                try {
                    for (segment in chapter.segments) {
                        if (workspace.result(segment) != null) continue
                        val response = client!!.translate(job.modelId!!, workspace.prompt(segment))
                        chapters.addUsage(jobId, chapter.index, response.inputTokens, response.outputTokens)
                        updateProgress(jobId)
                        workspace.replaceSegment(segment, response.content, response.inputTokens, response.outputTokens)
                    }
                    val record = chapters.findByJobId(jobId).first { it.chapterIndex == chapter.index }
                    chapters.markCompleted(jobId, chapter.index, record.inputTokens!!, record.outputTokens!!)
                    updateProgress(jobId)
                    complete = true
                    break
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val error = when (e) { is OpenRouterException -> e.code; is TranslationWorkspaceException -> "invalid_translation"; else -> "translation_request_failed" }
                    chapters.markFailed(jobId, chapter.index, error)
                    val retryable = e is TimeoutException || e is IOException || e is TranslationWorkspaceException || (e is OpenRouterException && e.retryable)
                    if (!retryable || attempt == 3) { jobs.markPaused(jobId, error, chapter.index); return }
                    delay(retryDelayMillis * attempt)
                }
            }
            if (!complete) return
        }
        try {
            library.publishTranslation(userId, job.sourceLibraryEntryId!!, jobId, workspace.publish(plan))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { jobs.markPaused(jobId, "publication_failed") }
    }

    private suspend fun validateModel(model: String) {
        val available = client ?: throw ValidationException("Translation is not configured")
        try {
            if (available.listFreeTextModels().none { it.id == model }) throw ValidationException("Selected translation model is no longer free")
        } catch (_: OpenRouterException) { throw ValidationException("Could not validate the free translation model") }
    }

    private fun updateProgress(jobId: String) {
        val all = chapters.findByJobId(jobId)
        jobs.updateProgress(jobId, all.count { it.status == "completed" }, null, all.sumOf { it.inputTokens!! }, all.sumOf { it.outputTokens!! })
    }

    private fun owned(userId: Int, jobId: String) = jobs.findByIdAndUserId(jobId, userId) ?: throw NotFoundException("Translation job not found")
    private fun validPlan(read: () -> TranslationPlan): TranslationPlan = try { read() } catch (_: TranslationWorkspaceException) { throw ValidationException("Invalid or unsupported EPUB") }
}

@Serializable data class TranslationEstimate(val totalChapters: Int, val estimatedInputTokens: Int, val modelId: String,
    val indicativeDuration: String = "minutes_to_hours", val limitWarning: String = "Free endpoints have rate limits and may pause translation.")
@Serializable data class TranslationStarted(val jobId: String, val status: String = "queued")
@Serializable data class TranslationStatus(val jobId: String, val status: String, val sourceLibraryEntryId: Int, val modelId: String,
    val totalChapters: Int, val completedChapters: Int, val failedChapterIndex: Int?, val estimatedInputTokens: Int,
    val actualInputTokens: Int, val actualOutputTokens: Int, val outputLibraryEntryId: Int?, val error: String?, val resumable: Boolean)
