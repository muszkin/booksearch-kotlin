package pl.fairydeck.booksearch.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class EpubTranslationWorkspaceTest {
    @TempDir lateinit var dir: Path

    @Test fun `diagnostics explain invalid output and accept a fenced JSON response`() {
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"))
        assertEquals(listOf("Tekst"), workspace.validateResponse(1, "```json\n[\"Tekst\"]\n```"))
        val count = assertThrows(TranslationWorkspaceException::class.java) { workspace.validateResponse(2, "[\"Tekst\"]") }
        assertEquals("item_count_mismatch", count.code)
        assertEquals("Expected 2 text items, received 1.", count.detail)
        assertEquals("invalid_json", assertThrows(TranslationWorkspaceException::class.java) { workspace.validateResponse(1, "secret invalid prose") }.code)
        assertEquals("empty_translation", assertThrows(TranslationWorkspaceException::class.java) { workspace.validateResponse(1, "[\"\"]") }.code)
    }

    @Test fun `exports translated inline text without original prose and marks partial chapters`() {
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"), maxSegmentCharacters = 12)
        val plan = workspace.create(translationFixture(dir.resolve("source.epub"), "<p>First block</p><p>Next block</p>"), "export")
        workspace.replaceSegment(plan.segments.first(), "[\"Pierwszy akapit\"]")
        val partial = workspace.exportChapter("export", 0, true)
        assertTrue(partial.startsWith("# Rozdział 1 — tłumaczenie częściowe"))
        assertTrue(partial.contains("Pierwszy akapit"))
        assertFalse(partial.contains("Next block"))
        workspace.replaceSegment(plan.segments[1], "[\"Drugi akapit\"]")
        val complete = workspace.exportChapter("export", 0, false)
        assertFalse(complete.contains("częściowe"))
        assertTrue(complete.contains("Pierwszy akapit\n\nDrugi akapit"))
    }

    @Test fun `rebuild preserves spine and non-text resources and escapes translated text`() {
        val source = translationFixture(dir.resolve("source.epub"))
        val original = source.readBytes()
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"))
        val plan = workspace.create(source, "job")
        assertEquals(listOf("OPS/two.xhtml", "OPS/one.xhtml"), plan.chapters.map { it.href })
        assertEquals(listOf("Hello ", "world", "!"), plan.segments.first().texts)
        workspace.replaceSegment(plan.segments.first(), """["Cześć ","świat & <świat>","!"]""")
        workspace.replaceSegment(plan.segments.last(), """["Drugi rozdział"]""")
        val output = workspace.publish(plan)
        ZipFile(output).use { zip ->
            val chapter = zip.getInputStream(zip.getEntry("OPS/two.xhtml")).readBytes().decodeToString()
            assertTrue(chapter.contains("<em class=\"accent\">świat &amp; &lt;świat&gt;</em>"))
            listOf("OPS/nav.xhtml", "OPS/style.css", "OPS/image.png", "META-INF/container.xml").forEach { name ->
                ZipFile(source).use { before -> assertArrayEquals(before.getInputStream(before.getEntry(name)).readBytes(), zip.getInputStream(zip.getEntry(name)).readBytes()) }
            }
        }
        assertEquals(2, workspace.inspect(output).chapters.size)
        assertArrayEquals(original, source.readBytes())
    }

    @Test fun `segments split at blocks and reject oversized single block`() {
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"), maxSegmentCharacters = 12)
        val source = translationFixture(dir.resolve("source.epub"), "<p>First block</p><p>Next block</p>")
        assertEquals(listOf(listOf("First block"), listOf("Next block")), workspace.create(source, "job").segments.take(2).map { it.texts })
        val large = translationFixture(dir.resolve("large.epub"), "<p>This block is too long to send</p>")
        assertThrows(TranslationWorkspaceException::class.java) { workspace.create(large, "large") }
    }

    @Test fun `invalid mapping never changes workspace and incomplete output cannot publish`() {
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"))
        val plan = workspace.create(translationFixture(dir.resolve("source.epub")), "job")
        assertThrows(TranslationWorkspaceException::class.java) { workspace.replaceSegment(plan.segments.first(), "[\"missing nodes\"]") }
        assertThrows(TranslationWorkspaceException::class.java) { workspace.publish(plan) }
    }

    @Test fun `rejects archive traversal and encrypted epub`() {
        val source = translationFixture(dir.resolve("bad.epub"), extraName = "../outside")
        assertThrows(TranslationWorkspaceException::class.java) { EpubTranslationWorkspace(dir.resolve("jobs")).inspect(source) }
        val encrypted = translationFixture(dir.resolve("encrypted.epub"), extraName = "META-INF/encryption.xml")
        assertThrows(TranslationWorkspaceException::class.java) { EpubTranslationWorkspace(dir.resolve("jobs")).inspect(encrypted) }
    }

    @Test fun `nested blocks keep text in reading order`() {
        val source = translationFixture(dir.resolve("nested.epub"), "<div>Before<p>Middle</p>After</div>")
        val plan = EpubTranslationWorkspace(dir.resolve("jobs")).create(source, "nested")
        assertEquals(listOf("Before", "Middle", "After"), plan.segments.first().texts)
    }

    @Test fun `preflight rejects oversized resource outside spine`() {
        val source = translationFixture(dir.resolve("large-resource.epub"), extraResources = mapOf("OPS/font.bin" to "x".repeat(4097)))
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"), maxEntryBytes = 4096)
        assertThrows(TranslationWorkspaceException::class.java) { workspace.create(source, "large") }
        assertFalse(dir.resolve("jobs/large").toFile().exists())
    }

    @Test fun `preflight rejects aggregate resource size even when each entry fits`() {
        val source = translationFixture(dir.resolve("large-total.epub"), extraResources = mapOf("OPS/font.bin" to "x".repeat(3000), "OPS/audio.bin" to "y".repeat(3000)))
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"), maxEntryBytes = 4096, maxArchiveBytes = 7000)
        assertThrows(TranslationWorkspaceException::class.java) { workspace.inspect(source) }
    }

    @Test fun `publication permits translation longer than input segment limit`() {
        val source = translationFixture(dir.resolve("source.epub"), "<p>Hello</p>")
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"), maxSegmentCharacters = 6)
        val plan = workspace.create(source, "expanded")
        workspace.replaceSegment(plan.segments.first(), """["Zdecydowanie dłuższe tłumaczenie"]""")
        workspace.replaceSegment(plan.segments.last(), """["Drugi rozdział również jest dłuższy"]""")
        val output = workspace.publish(plan)
        ZipFile(output).use { zip ->
            val chapter = zip.getInputStream(zip.getEntry("OPS/two.xhtml")).readBytes().decodeToString()
            assertTrue(chapter.contains("Zdecydowanie dłuższe tłumaczenie"))
        }
        assertEquals(2, EpubTranslationWorkspace(dir.resolve("readback")).inspect(output).chapters.size)
    }

    @Test fun `actual archive limit rejects falsely declared resource sizes`() {
        val source = translationFixture(dir.resolve("false-sizes.epub"), extraResources = mapOf("OPS/font.bin" to "x".repeat(3000), "OPS/audio.bin" to "y".repeat(3000)))
        declareZipEntrySize(source, "OPS/font.bin", 1)
        declareZipEntrySize(source, "OPS/audio.bin", 1)
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"), maxEntryBytes = 4096, maxArchiveBytes = 7000)
        assertThrows(TranslationWorkspaceException::class.java) { workspace.inspect(source) }
    }

    @Test fun `publication bounds streaming of an unchanged resource after snapshot modification`() {
        val source = translationFixture(dir.resolve("source.epub"))
        val workspace = EpubTranslationWorkspace(dir.resolve("jobs"), maxEntryBytes = 4096)
        val plan = workspace.create(source, "changed-snapshot")
        workspace.replaceSegment(plan.segments.first(), """["Cześć ","świat","!"]""")
        workspace.replaceSegment(plan.segments.last(), """["Drugi"]""")
        translationFixture(plan.source.toPath(), extraResources = mapOf("OPS/font.bin" to "x".repeat(4097)))
        declareZipEntrySize(plan.source, "OPS/font.bin", 1)
        assertThrows(TranslationWorkspaceException::class.java) { workspace.publish(plan) }
        assertTrue(source.exists())
    }
}

private fun declareZipEntrySize(file: java.io.File, entryName: String, size: Int) {
    val bytes = file.readBytes()
    val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    for (offset in 0 until bytes.size - 46) {
        if (buffer.getInt(offset) != 0x02014b50) continue
        val nameLength = buffer.getShort(offset + 28).toInt() and 0xffff
        if (bytes.copyOfRange(offset + 46, offset + 46 + nameLength).decodeToString() == entryName) {
            buffer.putInt(offset + 24, size)
            file.writeBytes(bytes)
            return
        }
    }
    error("Missing fixture ZIP entry")
}

internal fun translationFixture(path: Path, first: String = "<p>Hello <em class=\"accent\">world</em>!</p>", extraName: String? = null, extraResources: Map<String, String> = emptyMap(), language: String = "en"): java.io.File {
    val entries = linkedMapOf(
        "mimetype" to "application/epub+zip",
        "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>""",
        "OPS/book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">fixture</dc:identifier><dc:title>Source</dc:title><dc:language>en</dc:language></metadata><manifest><item id="one" href="one.xhtml" media-type="application/xhtml+xml"/><item id="two" href="two.xhtml" media-type="application/xhtml+xml"/><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/></manifest><spine><itemref idref="two"/><itemref idref="one"/></spine></package>""",
        "OPS/one.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Second</title></head><body><p>Second</p></body></html>""",
        "OPS/two.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>First</title><style>.x { color: red }</style></head><body>$first<img src="image.png"/><script>privateScript()</script></body></html>""",
        "OPS/nav.xhtml" to "<html><body>Navigation untouched</body></html>",
        "OPS/style.css" to "p { color: red; }",
        "OPS/image.png" to "binary-image-fixture"
    )
    entries["OPS/book.opf"] = entries.getValue("OPS/book.opf").replace("<dc:language>en</dc:language>", "<dc:language>$language</dc:language>")
    extraName?.let { entries[it] = "invalid" }
    entries.putAll(extraResources)
    ZipOutputStream(path.toFile().outputStream()).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
    return path.toFile()
}
