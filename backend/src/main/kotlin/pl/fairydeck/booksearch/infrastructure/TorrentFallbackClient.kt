package pl.fairydeck.booksearch.infrastructure

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TorrentFallbackClient(
    private val config: ScraperConfig,
    private val httpClient: ImpersonatorHttpClient
) {

    private val logger = LoggerFactory.getLogger(TorrentFallbackClient::class.java)
    private val torrentMutex = Mutex()

    suspend fun download(
        jobId: Int,
        bookMd5: String,
        format: String,
        mirror: String,
        link: TorrentDownloadLink,
        onProgress: (TorrentProgress) -> Unit = {}
    ): ByteArray {
        require(config.torrentFallbackEnabled) { "Torrent fallback is disabled" }
        require(!link.isPacked) { "Torrent fallback does not support packed source files" }

        return torrentMutex.withLock {
            val torrentUrl = resolveTorrentUrl(mirror, link.torrentUrl)
            logger.info("Job {}: downloading public torrent metadata from {}", jobId, mirror)
            onProgress(TorrentProgress.FetchingMetadata)

            val torrentBytes = httpClient.fetchBinary(torrentUrl, emptyMap())
            val selection = TorrentMetadataParser.findFile(torrentBytes, link.fileLevel1)
            logger.info(
                "Job {}: torrent fallback selected file {} (aria2 index {}, {} bytes, piece {} bytes)",
                jobId,
                link.fileLevel1,
                selection.aria2Index,
                selection.fileSize,
                selection.pieceLength
            )

            withContext(Dispatchers.IO) {
                downloadSelectedFile(
                    jobId = jobId,
                    bookMd5 = bookMd5,
                    format = format,
                    torrentBytes = torrentBytes,
                    selection = selection,
                    onProgress = onProgress
                )
            }
        }
    }

    private fun downloadSelectedFile(
        jobId: Int,
        bookMd5: String,
        format: String,
        torrentBytes: ByteArray,
        selection: TorrentFileSelection,
        onProgress: (TorrentProgress) -> Unit
    ): ByteArray {
        val jobsRoot = File(File(config.dataPath).absoluteFile.parentFile, "torrent-jobs")
        val jobDirectory = File(jobsRoot, jobId.toString())
        if (jobDirectory.exists() && !jobDirectory.deleteRecursively()) {
            throw IOException("Could not clean the previous torrent staging directory")
        }
        if (!jobDirectory.mkdirs()) {
            throw IOException("Could not create torrent staging directory")
        }

        val torrentFile = File(jobDirectory, "$bookMd5.torrent")
        torrentFile.writeBytes(torrentBytes)

        try {
            onProgress(TorrentProgress.WaitingForPeers)
            val outputTail = ConcurrentLinkedDeque<String>()
            val lastReportedPercent = AtomicInteger(-1)
            val process = startAria2(jobDirectory, torrentFile, selection)
            val outputThread = Thread(
                {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            appendOutputLine(outputTail, line)
                            val percent = ARIA2_PROGRESS_PATTERN.find(line)
                                ?.groupValues
                                ?.getOrNull(1)
                                ?.toIntOrNull()
                            if (
                                percent != null &&
                                percent > 0 &&
                                percent > lastReportedPercent.getAndSet(percent)
                            ) {
                                onProgress(TorrentProgress.Downloading(percent))
                            }
                        }
                    }
                },
                "torrent-output-$jobId"
            ).apply {
                isDaemon = true
                start()
            }

            val finished = process.waitFor(MAX_TORRENT_RUNTIME_MINUTES, TimeUnit.MINUTES)
            if (!finished) {
                stopProcess(process)
                throw IOException("Torrent fallback exceeded the maximum runtime")
            }
            outputThread.join(OUTPUT_THREAD_JOIN_TIMEOUT_MS)

            if (process.exitValue() != 0) {
                val output = outputTail.joinToString(" | ").takeLast(MAX_ERROR_OUTPUT_LENGTH)
                throw IOException(formatFailure(output, config.torrentStallTimeoutSeconds))
            }

            val selectedFile = resolveSelectedFile(jobDirectory, selection)
            if (!selectedFile.isFile) {
                throw IOException("Torrent fallback completed without the selected file")
            }
            if (selectedFile.length() != selection.fileSize) {
                throw IOException(
                    "Torrent fallback returned ${selectedFile.length()} bytes, expected ${selection.fileSize}"
                )
            }

            val bytes = selectedFile.readBytes()
            val actualMd5 = md5(bytes)
            if (!actualMd5.equals(bookMd5, ignoreCase = true)) {
                throw IOException(
                    "Torrent fallback checksum mismatch for $format file"
                )
            }
            return bytes
        } finally {
            if (!jobDirectory.deleteRecursively()) {
                logger.warn("Job {}: could not fully remove torrent staging directory", jobId)
            }
        }
    }

    private fun startAria2(
        jobDirectory: File,
        torrentFile: File,
        selection: TorrentFileSelection
    ): Process {
        val command = listOf(
            "aria2c",
            "--no-conf=true",
            "--enable-color=false",
            "--summary-interval=5",
            "--console-log-level=notice",
            "--download-result=full",
            "--file-allocation=none",
            "--seed-time=0",
            "--bt-enable-lpd=false",
            "--bt-remove-unselected-file=true",
            "--bt-stop-timeout=${config.torrentStallTimeoutSeconds}",
            "--select-file=${selection.aria2Index}",
            "--dir=${jobDirectory.absolutePath}",
            torrentFile.absolutePath
        )

        return try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            throw IOException("aria2c is not available in the backend container", e)
        }
    }

    private fun resolveSelectedFile(
        jobDirectory: File,
        selection: TorrentFileSelection
    ): File {
        val relativePath = if (selection.rootName.isBlank()) {
            selection.relativePath
        } else {
            "${selection.rootName}/${selection.relativePath}"
        }
        val jobRoot = jobDirectory.canonicalFile.toPath()
        val selected = File(jobDirectory, relativePath).canonicalFile
        require(selected.toPath().startsWith(jobRoot)) {
            "Torrent selected file escaped the staging directory"
        }
        return selected
    }

    private fun resolveTorrentUrl(mirror: String, torrentUrl: String): String {
        if (!torrentUrl.startsWith("http")) return "$mirror$torrentUrl"

        val uri = URI(torrentUrl)
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        return "$mirror${uri.rawPath}$query"
    }

    private fun stopProcess(process: Process) {
        process.destroy()
        if (!process.waitFor(PROCESS_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
    }

    private fun appendOutputLine(outputTail: ConcurrentLinkedDeque<String>, line: String) {
        if (line.isBlank()) return
        outputTail.addLast(line.trim())
        while (outputTail.size > MAX_OUTPUT_LINES) {
            outputTail.pollFirst()
        }
    }

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_TORRENT_RUNTIME_MINUTES = 45L
        private const val PROCESS_STOP_TIMEOUT_SECONDS = 5L
        private const val OUTPUT_THREAD_JOIN_TIMEOUT_MS = 5_000L
        private const val MAX_OUTPUT_LINES = 20
        private const val MAX_ERROR_OUTPUT_LENGTH = 1_000
        private val ARIA2_PROGRESS_PATTERN = Regex("""\((\d{1,3})%\)""")

        internal fun formatFailure(output: String, stallTimeoutSeconds: Long): String {
            if (output.contains("bt-stop-timeout", ignoreCase = true)) {
                return "No torrent data became available within $stallTimeoutSeconds seconds"
            }
            return "Torrent fallback stopped before the file was available" +
                output.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
        }
    }
}

sealed interface TorrentProgress {
    data object FetchingMetadata : TorrentProgress
    data object WaitingForPeers : TorrentProgress
    data class Downloading(val percent: Int) : TorrentProgress
}
