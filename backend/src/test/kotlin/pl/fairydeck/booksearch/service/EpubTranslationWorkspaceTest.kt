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
}

internal fun translationFixture(path: Path, first: String = "<p>Hello <em class=\"accent\">world</em>!</p>", extraName: String? = null): java.io.File {
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
    extraName?.let { entries[it] = "invalid" }
    ZipOutputStream(path.toFile().outputStream()).use { zip -> entries.forEach { (name, text) -> zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() } }
    return path.toFile()
}
