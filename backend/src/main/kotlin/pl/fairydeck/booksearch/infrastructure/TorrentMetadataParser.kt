package pl.fairydeck.booksearch.infrastructure

import java.nio.charset.StandardCharsets

object TorrentMetadataParser {

    fun findFile(torrentBytes: ByteArray, expectedFileName: String): TorrentFileSelection {
        require(expectedFileName.isNotBlank()) { "Torrent target file name is empty" }

        val root = BencodeParser(torrentBytes).parseDictionary()
        val info = root.dictionary("info")
            ?: throw IllegalArgumentException("Torrent metadata does not contain an info dictionary")
        val rootName = info.utf8String("name")
            ?: throw IllegalArgumentException("Torrent metadata does not contain a name")
        validatePathComponent(rootName)

        val files = info.list("files")
        if (files == null) {
            if (rootName != expectedFileName) {
                throw IllegalArgumentException("Torrent does not contain the expected file")
            }
            return TorrentFileSelection(
                aria2Index = 1,
                rootName = "",
                relativePath = rootName,
                fileSize = info.integer("length")
                    ?: throw IllegalArgumentException("Torrent metadata does not contain a file length"),
                pieceLength = info.integer("piece length") ?: 0L
            )
        }

        val matches = files.mapIndexedNotNull { index, value ->
            val file = value as? BencodeValue.Dictionary ?: return@mapIndexedNotNull null
            val path = file.utf8Path()
            if (path.lastOrNull() != expectedFileName) return@mapIndexedNotNull null

            path.forEach(::validatePathComponent)
            TorrentFileSelection(
                aria2Index = index + 1,
                rootName = rootName,
                relativePath = path.joinToString("/"),
                fileSize = file.integer("length")
                    ?: throw IllegalArgumentException("Torrent file entry does not contain a length"),
                pieceLength = info.integer("piece length") ?: 0L
            )
        }

        if (matches.size != 1) {
            throw IllegalArgumentException(
                "Expected exactly one torrent file named '$expectedFileName', found ${matches.size}"
            )
        }
        return matches.single()
    }

    private fun BencodeValue.Dictionary.utf8Path(): List<String> {
        val values = list("path.utf-8") ?: list("path")
            ?: throw IllegalArgumentException("Torrent file entry does not contain a path")
        return values.map { value ->
            (value as? BencodeValue.Bytes)?.utf8()
                ?: throw IllegalArgumentException("Torrent path contains a non-string component")
        }
    }

    private fun BencodeValue.Dictionary.utf8String(key: String): String? {
        val utf8Value = bytes("$key.utf-8") ?: bytes(key)
        return utf8Value?.utf8()
    }

    private fun BencodeValue.Bytes.utf8(): String =
        String(value, StandardCharsets.UTF_8)

    private fun validatePathComponent(component: String) {
        require(
            component.isNotBlank() &&
                component != "." &&
                component != ".." &&
                !component.contains('/') &&
                !component.contains('\\') &&
                !component.contains('\u0000')
        ) {
            "Unsafe path component in torrent metadata"
        }
    }
}

data class TorrentFileSelection(
    val aria2Index: Int,
    val rootName: String,
    val relativePath: String,
    val fileSize: Long,
    val pieceLength: Long
)

private sealed interface BencodeValue {
    data class Integer(val value: Long) : BencodeValue
    data class Bytes(val value: ByteArray) : BencodeValue
    data class ListValue(val value: List<BencodeValue>) : BencodeValue
    data class Dictionary(val value: Map<String, BencodeValue>) : BencodeValue {
        fun dictionary(key: String): Dictionary? = value[key] as? Dictionary
        fun list(key: String): List<BencodeValue>? = (value[key] as? ListValue)?.value
        fun bytes(key: String): Bytes? = value[key] as? Bytes
        fun integer(key: String): Long? = (value[key] as? Integer)?.value
    }
}

private class BencodeParser(private val input: ByteArray) {
    private var offset = 0

    fun parseDictionary(): BencodeValue.Dictionary {
        val value = parseValue(depth = 0) as? BencodeValue.Dictionary
            ?: throw IllegalArgumentException("Torrent metadata root is not a dictionary")
        require(offset == input.size) { "Unexpected trailing data in torrent metadata" }
        return value
    }

    private fun parseValue(depth: Int): BencodeValue {
        require(depth <= MAX_DEPTH) { "Torrent metadata is nested too deeply" }
        require(offset < input.size) { "Unexpected end of torrent metadata" }

        return when (input[offset].toInt().toChar()) {
            'i' -> parseInteger()
            'l' -> parseList(depth)
            'd' -> parseDictionaryValue(depth)
            in '0'..'9' -> BencodeValue.Bytes(parseBytes())
            else -> throw IllegalArgumentException("Invalid bencode token at offset $offset")
        }
    }

    private fun parseInteger(): BencodeValue.Integer {
        offset++
        val end = findDelimiter('e')
        val encoded = input.decodeToString(offset, end)
        require(encoded.matches(INTEGER_PATTERN)) { "Invalid bencode integer" }
        offset = end + 1
        return BencodeValue.Integer(encoded.toLong())
    }

    private fun parseList(depth: Int): BencodeValue.ListValue {
        offset++
        val values = mutableListOf<BencodeValue>()
        while (!consumeEndMarker()) {
            require(values.size < MAX_COLLECTION_SIZE) { "Torrent list is too large" }
            values += parseValue(depth + 1)
        }
        return BencodeValue.ListValue(values)
    }

    private fun parseDictionaryValue(depth: Int): BencodeValue.Dictionary {
        offset++
        val values = linkedMapOf<String, BencodeValue>()
        while (!consumeEndMarker()) {
            require(values.size < MAX_COLLECTION_SIZE) { "Torrent dictionary is too large" }
            val key = String(parseBytes(), StandardCharsets.UTF_8)
            values[key] = parseValue(depth + 1)
        }
        return BencodeValue.Dictionary(values)
    }

    private fun parseBytes(): ByteArray {
        val colon = findDelimiter(':')
        val lengthText = input.decodeToString(offset, colon)
        require(lengthText.matches(LENGTH_PATTERN)) { "Invalid bencode byte-string length" }
        val length = lengthText.toInt()
        require(length <= MAX_BYTE_STRING_SIZE) { "Torrent byte string is too large" }

        val start = colon + 1
        val end = start + length
        require(end <= input.size) { "Unexpected end of torrent byte string" }
        offset = end
        return input.copyOfRange(start, end)
    }

    private fun consumeEndMarker(): Boolean {
        require(offset < input.size) { "Unexpected end of torrent collection" }
        if (input[offset].toInt().toChar() != 'e') return false
        offset++
        return true
    }

    private fun findDelimiter(delimiter: Char): Int {
        for (index in offset until input.size) {
            if (input[index].toInt().toChar() == delimiter) return index
        }
        throw IllegalArgumentException("Missing '$delimiter' delimiter in torrent metadata")
    }

    companion object {
        private const val MAX_DEPTH = 32
        private const val MAX_COLLECTION_SIZE = 100_000
        private const val MAX_BYTE_STRING_SIZE = 64 * 1024 * 1024
        private val INTEGER_PATTERN = Regex("""-?(0|[1-9]\d*)""")
        private val LENGTH_PATTERN = Regex("""0|[1-9]\d*""")
    }
}
