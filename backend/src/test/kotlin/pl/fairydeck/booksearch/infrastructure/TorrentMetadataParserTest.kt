package pl.fairydeck.booksearch.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class TorrentMetadataParserTest {

    @Test
    fun shouldFindExactFileAndReturnOneBasedAria2Index() {
        val torrent = dictionary(
            "announce" to bytes("https://tracker.example/announce"),
            "info" to dictionary(
                "name" to bytes("book-batch"),
                "piece length" to integer(268_435_456),
                "pieces" to bytes(ByteArray(20)),
                "files" to list(
                    dictionary(
                        "length" to integer(100),
                        "path" to list(bytes("first-file"))
                    ),
                    dictionary(
                        "length" to integer(462_841),
                        "path" to list(bytes("aacid__target"))
                    )
                )
            )
        )

        val selection = TorrentMetadataParser.findFile(torrent, "aacid__target")

        assertEquals(2, selection.aria2Index)
        assertEquals("book-batch", selection.rootName)
        assertEquals("aacid__target", selection.relativePath)
        assertEquals(462_841, selection.fileSize)
        assertEquals(268_435_456, selection.pieceLength)
    }

    @Test
    fun shouldRejectUnsafeTorrentPaths() {
        val torrent = dictionary(
            "info" to dictionary(
                "name" to bytes("book-batch"),
                "files" to list(
                    dictionary(
                        "length" to integer(100),
                        "path" to list(bytes(".."), bytes("aacid__target"))
                    )
                )
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            TorrentMetadataParser.findFile(torrent, "aacid__target")
        }
    }

    private fun dictionary(vararg values: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        output.write('d'.code)
        values.sortedBy { it.first }.forEach { (key, value) ->
            output.write(bytes(key))
            output.write(value)
        }
        output.write('e'.code)
        return output.toByteArray()
    }

    private fun list(vararg values: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write('l'.code)
        values.forEach(output::write)
        output.write('e'.code)
        return output.toByteArray()
    }

    private fun integer(value: Long): ByteArray = "i${value}e".toByteArray()

    private fun bytes(value: String): ByteArray = bytes(value.toByteArray())

    private fun bytes(value: ByteArray): ByteArray =
        "${value.size}:".toByteArray() + value
}
