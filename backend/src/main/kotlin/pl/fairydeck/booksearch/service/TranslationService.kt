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
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class TranslationService(
    private val jobs: TranslationJobRepository,
    private val chapters: TranslationChapterRepository,
    private val settings: SystemConfigRepository,
    private val library: LibraryService,
    private val workspace: EpubTranslationWorkspace,
    private val client: OpenRouterClient?,
    private val scope: CoroutineScope,
    private val activityLog: ActivityLogService,
    private val retryDelayMillis: Long = 1000
) {
    private val requests = Mutex()
    private val transitions = Any()
    private val logger = LoggerFactory.getLogger(TranslationService::class.java)

    fun references(userId: Int, sourceId: Int) = library.translationReferences(userId, sourceId)

    fun validateReferenceDownload(userId: Int, sourceId: Int, md5: String) =
        library.validateTranslationReferenceDownload(userId, sourceId, md5)

    suspend fun previewContext(userId: Int, sourceId: Int, options: TranslationOptions): TranslationOptions {
        library.translationSource(userId, sourceId)
        return prepareOptions(userId, sourceId, options)
    }

    fun details(userId: Int, jobId: String): TranslationDetails {
        owned(userId, jobId)
        val plan = validPlan { workspace.load(jobId) }
        return TranslationDetails(workspace.options(jobId), chapters.findByJobId(jobId).map { row ->
            val chapter = plan.chapters[row.chapterIndex!!]
            TranslationChapterDetail(row.chapterIndex!!, row.href!!, row.status!!, row.attempts!!, row.error,
                chapter.segments.count { workspace.result(it) != null }, chapter.segments.size)
        }, workspace.attempts(jobId))
    }

    fun exportChapter(userId: Int, jobId: String, index: Int, format: String): String {
        owned(userId, jobId)
        if (format !in listOf("txt", "md")) throw ValidationException("Choose txt or md")
        val plan = validPlan { workspace.load(jobId) }
        val chapter = plan.chapters.getOrNull(index) ?: throw NotFoundException("Chapter not found")
        if (chapter.segments.none { workspace.result(it) != null }) throw ValidationException("No translated text yet in this chapter")
        return workspace.exportChapter(jobId, index, format == "md")
    }

    private suspend fun prepareOptions(userId: Int, sourceId: Int, options: TranslationOptions, previous: TranslationOptions? = null): TranslationOptions {
        if (options.glossary.length > 12000 || options.notes.length > 4000 || options.referenceLibraryIds.size > 5 ||
            options.referenceChapters !in 1..5 || options.fallbackModelIds.size > 10) throw ValidationException("Translation context or model list exceeds limits")
        options.modelId?.let { validateModel(it) }
        options.fallbackModelIds.forEach { validateModel(it) }
        if (previous != null && previous.referenceLibraryIds == options.referenceLibraryIds && previous.referenceChapters == options.referenceChapters)
            return options.copy(referenceText = previous.referenceText)
        val excerpts = options.referenceLibraryIds.distinct().map { id ->
            val reference = library.translationReference(userId, sourceId, id)
            val plan = validPlan { workspace.inspect(reference) }
            if (!isPolish(plan.language)) throw ValidationException("Reference EPUB must declare Polish language")
            // Stable across preview/start; the persisted sample is reused on resume.
            val sampled = plan.chapters.filter { it.segments.isNotEmpty() }.shuffled(kotlin.random.Random(sourceId * 31 + id)).take(options.referenceChapters)
            "Reference library entry $id, chapters ${sampled.map { it.index + 1 }}:\n" +
                sampled.joinToString("\n") { it.segments.flatMap { s -> s.texts }.joinToString(" ").take(1600) }
        }.joinToString("\n\n").take(16000)
        return options.copy(referenceText = excerpts)
    }

    suspend fun estimate(userId: Int, sourceLibraryEntryId: Int, selectedModel: String? = null): TranslationEstimate {
        val source = library.translationSource(userId, sourceLibraryEntryId)
        val model = selectedModel ?: settings.getTranslationDefaultModel() ?: throw ValidationException("Translation is not configured")
        validateModel(model)
        val plan = validPlan { workspace.inspect(source.file) }
        if (!isEnglish(plan.language)) throw ValidationException("Translation requires an English EPUB")
        return TranslationEstimate(plan.chapters.size, plan.estimatedInputTokens, model)
    }

    suspend fun start(userId: Int, sourceLibraryEntryId: Int, externalProcessingConfirmed: Boolean, options: TranslationOptions = TranslationOptions()): TranslationStarted {
        if (!externalProcessingConfirmed) throw ValidationException("External processing confirmation is required")
        val estimate = estimate(userId, sourceLibraryEntryId, options.modelId)
        val prepared = prepareOptions(userId, sourceLibraryEntryId, options)
        return synchronized(transitions) {
            if (jobs.findActiveBySourceLibraryEntryId(userId, sourceLibraryEntryId) != null) throw ConflictException("Translation already active for this EPUB")
            val source = library.translationSource(userId, sourceLibraryEntryId)
            val id = jobs.create(userId, sourceLibraryEntryId, prepared.modelId ?: estimate.modelId)
            try {
                val plan = workspace.create(source.file, id)
                workspace.saveOptions(id, prepared)
                jobs.updatePlan(id, source.bookMd5, source.file.absolutePath, plan.directory.toString(), plan.chapters.size, plan.estimatedInputTokens)
                plan.chapters.forEach { chapters.create(id, it.index, it.href) }
                activityLog.log(userId, "TRANSLATION_STARTED", "translation_job", id, "sourceLibraryEntryId=$sourceLibraryEntryId")
                enqueue(userId, id)
                TranslationStarted(id)
            } catch (_: Exception) {
                jobs.markFailed(id, "workspace_failed")
                throw ValidationException("Could not prepare EPUB translation")
            }
        }
    }

    fun listActive(userId: Int): List<TranslationStatus> = jobs.findVisibleByUserId(userId).map(::toStatus)

    fun status(userId: Int, jobId: String): TranslationStatus = toStatus(owned(userId, jobId))

    private fun toStatus(job: pl.fairydeck.booksearch.jooq.generated.tables.records.TranslationJobsRecord): TranslationStatus {
        return TranslationStatus(job.id!!, job.status!!, job.sourceLibraryEntryId!!, job.modelId!!,
            job.totalChapters!!, job.completedChapters!!, job.failedChapterIndex, job.estimatedInputTokens!!,
            job.actualInputTokens!!, job.actualOutputTokens!!, job.outputLibraryEntryId, job.error, job.status == "paused")
    }

    suspend fun resume(userId: Int, jobId: String, options: TranslationOptions? = null): TranslationStarted {
        val job = owned(userId, jobId)
        if (job.status != "paused") throw ConflictException("Only paused translations can resume")
        val source = library.translationSource(userId, job.sourceLibraryEntryId!!)
        if (source.bookMd5 != job.sourceBookMd5 || source.file.absolutePath != job.sourceFilePath) throw ValidationException("Translation source changed")
        val plan = validPlan { workspace.load(jobId) }
        if (java.nio.file.Files.mismatch(source.file.toPath(), plan.source.toPath()) != -1L) throw ValidationException("Translation source changed")
        val previous = workspace.options(jobId)
        val prepared = if (options != null) prepareOptions(userId, job.sourceLibraryEntryId!!, options, previous) else previous
        val preferred = prepared.modelId ?: job.modelId!!
        if (!prepared.autoFallback) validateModel(preferred)
        else if (client?.listFreeTextModels().isNullOrEmpty()) throw ValidationException("No free models available")
        return synchronized(transitions) {
            if (owned(userId, jobId).status != "paused") throw ConflictException("Translation is no longer paused")
            if (options != null) workspace.saveOptions(jobId, prepared)
            jobs.setModel(jobId, preferred)
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
        val options = workspace.options(jobId)
        val free = client!!.listFreeTextModels().map { it.id }
        val candidates = if (!options.autoFallback) listOf(job.modelId!!) else
            (listOf(job.modelId!!) + options.fallbackModelIds.ifEmpty { free.filter { !it.startsWith("openrouter/") } }).distinct().filter { it in free }.take(5)
        if (candidates.isEmpty()) { jobs.markPaused(jobId, "model_no_longer_free"); return }
        var currentModel = candidates.first()
        for (chapter in plan.chapters) {
            if (chapters.findByJobId(jobId).first { it.chapterIndex == chapter.index }.status == "completed") continue
            var complete = false
            val chapterModels = (listOf(currentModel) + candidates).distinct()
            val unavailable = mutableSetOf<String>()
            for (attempt in 1..(3 * chapterModels.size)) {
                val model = chapterModels[(attempt - 1) / 3]
                if (model in unavailable) continue
                chapters.markAttemptStarted(jobId, chapter.index)
                try {
                    for (segment in chapter.segments) {
                        if (workspace.result(segment) != null) continue
                        translateSegment(jobId, segment, model, options, smaller = attempt % 3 != 1)
                    }
                    currentModel = model
                    val record = chapters.findByJobId(jobId).first { it.chapterIndex == chapter.index }
                    chapters.markCompleted(jobId, chapter.index, record.inputTokens!!, record.outputTokens!!)
                    updateProgress(jobId)
                    complete = true
                    break
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val error = when (e) { is OpenRouterException -> e.code; is TranslationWorkspaceException -> e.code; else -> "translation_request_failed" }
                    chapters.markFailed(jobId, chapter.index, error)
                    val retryable = e is TimeoutException || e is IOException || e is TranslationWorkspaceException || (e is OpenRouterException && e.retryable)
                    if (error == "model_no_longer_free") {
                        unavailable.add(model)
                        if (model == chapterModels.last()) { jobs.markPaused(jobId, error, chapter.index); return }
                        continue
                    }
                    if ((!retryable && (!options.autoFallback || error in listOf("http_401", "http_402", "http_403"))) || attempt == 3 * candidates.size) { jobs.markPaused(jobId, error, chapter.index); return }
                    delay(retryDelayMillis * minOf(attempt, 3))
                }
            }
            if (!complete) return
        }
        try {
            val outputId = library.publishTranslation(userId, job.sourceLibraryEntryId!!, jobId, workspace.publish(plan))
            activityLog.log(userId, "TRANSLATION_COMPLETED", "translation_job", jobId, "outputLibraryEntryId=$outputId")
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { jobs.markPaused(jobId, "publication_failed") }
    }

    private suspend fun translateSegment(jobId: String, segment: TranslationSegment, model: String, options: TranslationOptions, smaller: Boolean) {
        val groups = if (smaller) segment.texts.indices.chunked(12) else listOf(segment.texts.indices.toList())
        val combined = mutableListOf<String>()
        var inputs = 0; var outputs = 0
        for (indices in groups) {
            val part = segment.copy(texts = indices.map { segment.texts[it] }, nodes = indices.map { segment.nodes[it] })
            var event = TranslationAttempt(chapterIndex = segment.chapterIndex, segmentIndex = segment.index, requestedModel = model, itemCount = part.texts.size)
            workspace.recordAttempt(jobId, event)
            try {
                val response = client!!.translate(model, workspace.prompt(part, options.promptContext()))
                inputs += response.inputTokens; outputs += response.outputTokens
                chapters.addUsage(jobId, segment.chapterIndex, response.inputTokens, response.outputTokens)
                updateProgress(jobId)
                event = event.copy(actualModel = response.model, inputTokens = response.inputTokens, outputTokens = response.outputTokens, finishReason = response.finishReason)
                if (response.finishReason == "length") throw TranslationWorkspaceException("output_truncated", "Model exhausted its output limit before completing the translation.")
                if (response.content.isBlank()) throw TranslationWorkspaceException("empty_response", "Model returned no translation text.")
                combined.addAll(workspace.validateResponse(part.texts.size, response.content))
                workspace.recordAttempt(jobId, event.copy(status = "completed"))
            } catch (e: CancellationException) {
                workspace.recordAttempt(jobId, event.copy(status = "interrupted", errorCode = "server_restart", message = "Request interrupted; resume is available.")); throw e
            } catch (e: Exception) {
                val code = when (e) { is TranslationWorkspaceException -> e.code; is OpenRouterException -> e.code; else -> "translation_request_failed" }
                val message = when (e) { is TranslationWorkspaceException -> e.detail; is OpenRouterException -> "OpenRouter request failed ($code)."; else -> "Request failed before a valid translation was received." }
                workspace.recordAttempt(jobId, event.copy(status = "failed", errorCode = code, message = message))
                logger.warn("Translation job={} chapter={} segment={} model={} code={}", jobId, segment.chapterIndex, segment.index, model, code)
                throw e
            }
        }
        workspace.replaceSegment(segment, Json.encodeToString(combined), inputs, outputs)
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
