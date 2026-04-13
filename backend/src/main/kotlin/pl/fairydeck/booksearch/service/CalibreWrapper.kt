package pl.fairydeck.booksearch.service

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

class CalibreWrapper(
    private val ebookConvertPath: String = "ebook-convert",
    private val timeoutMinutes: Long = 10
) {

    private val logger = LoggerFactory.getLogger(CalibreWrapper::class.java)

    fun convert(inputFile: File, outputFile: File) {
        if (!inputFile.exists()) {
            throw IllegalArgumentException("Input file does not exist: ${inputFile.absolutePath}")
        }

        val outputProfile = resolveOutputProfile(outputFile.extension.lowercase())

        val command = mutableListOf(
            ebookConvertPath,
            inputFile.absolutePath,
            outputFile.absolutePath
        )

        if (outputProfile != null) {
            command.add("--output-profile")
            command.add(outputProfile)
        }

        logger.info("Running Calibre conversion: {} -> {}", inputFile.name, outputFile.name)

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)

        if (!exitCode) {
            process.destroyForcibly()
            throw IllegalStateException("Calibre conversion timed out after $timeoutMinutes minutes")
        }

        if (process.exitValue() != 0) {
            logger.error("Calibre conversion failed (exit code {}): {}", process.exitValue(), output)
            throw IllegalStateException("Calibre conversion failed with exit code ${process.exitValue()}: ${output.take(500)}")
        }

        logger.info("Calibre conversion completed successfully: {}", outputFile.name)
    }

    private fun resolveOutputProfile(targetExtension: String): String? = when (targetExtension) {
        "mobi" -> "kindle"
        else -> null
    }
}
