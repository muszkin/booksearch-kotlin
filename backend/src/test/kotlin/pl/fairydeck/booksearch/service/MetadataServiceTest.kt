package pl.fairydeck.booksearch.service

import io.documentnode.epub4j.domain.Author
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.epub.EpubWriter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path

class MetadataServiceTest {

    private lateinit var metadataService: MetadataService

    @TempDir
    lateinit var tempDir: Path

    private lateinit var epubWithCover: File
    private lateinit var epubWithoutCover: File
    private lateinit var corruptFile: File

    @BeforeEach
    fun setUp() {
        metadataService = MetadataService()
        epubWithCover = createEpubWithCover()
        epubWithoutCover = createEpubWithoutCover()
        corruptFile = createCorruptFile()
    }

    @Test
    fun shouldExtractTitleAndAuthorFromEpub() {
        val metadata = metadataService.extractMetadata(epubWithCover.toPath())

        assertEquals("Test Book Title", metadata.title)
        assertEquals("John Doe", metadata.author)
        assertEquals("A test description", metadata.description)
        assertEquals("en", metadata.language)
        assertEquals("Test Publisher", metadata.publisher)
    }

    @Test
    fun shouldExtractCoverImageWhenPresent() {
        val metadata = metadataService.extractMetadata(epubWithCover.toPath())

        assertNotNull(metadata.coverBytes)
        assertTrue(metadata.coverBytes!!.isNotEmpty())
    }

    @Test
    fun shouldReturnNullCoverWhenNoCoverImage() {
        val metadata = metadataService.extractMetadata(epubWithoutCover.toPath())

        assertNull(metadata.coverBytes)
        assertEquals("No Cover Book", metadata.title)
        assertEquals("Jane Smith", metadata.author)
    }

    @Test
    fun shouldThrowMetadataExtractionExceptionForCorruptFile() {
        assertThrows(MetadataExtractionException::class.java) {
            metadataService.extractMetadata(corruptFile.toPath())
        }
    }

    @Test
    fun shouldSaveCoverImageToDirectory() {
        val coverBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val outputDir = tempDir.resolve("covers").toFile()
        outputDir.mkdirs()

        val savedPath = metadataService.saveCoverImage(coverBytes, outputDir, "abc123")

        assertEquals("abc123_cover.jpg", File(savedPath).name)
        assertTrue(File(savedPath).exists())
        assertArrayEquals(coverBytes, File(savedPath).readBytes())
    }

    private fun createEpubWithCover(): File {
        val book = Book()
        book.metadata.addTitle("Test Book Title")
        book.metadata.addAuthor(Author("John", "Doe"))
        book.metadata.addDescription("A test description")
        book.metadata.language = "en"
        book.metadata.addPublisher("Test Publisher")

        val coverData = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
        )
        val coverResource = Resource(coverData, "cover.png")
        book.coverImage = coverResource

        val contentHtml = "<html><body><p>Test content</p></body></html>"
        book.addSection("Chapter 1", Resource(contentHtml.toByteArray(), "chapter1.html"))

        val file = tempDir.resolve("test_with_cover.epub").toFile()
        FileOutputStream(file).use { EpubWriter().write(book, it) }
        return file
    }

    private fun createEpubWithoutCover(): File {
        val book = Book()
        book.metadata.addTitle("No Cover Book")
        book.metadata.addAuthor(Author("Jane", "Smith"))
        book.metadata.addDescription("Book without cover")
        book.metadata.language = "pl"
        book.metadata.addPublisher("Another Publisher")

        val contentHtml = "<html><body><p>Content without cover</p></body></html>"
        book.addSection("Chapter 1", Resource(contentHtml.toByteArray(), "chapter1.html"))

        val file = tempDir.resolve("test_without_cover.epub").toFile()
        FileOutputStream(file).use { EpubWriter().write(book, it) }
        return file
    }

    private fun createCorruptFile(): File {
        val file = tempDir.resolve("corrupt.epub").toFile()
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))
        return file
    }
}
