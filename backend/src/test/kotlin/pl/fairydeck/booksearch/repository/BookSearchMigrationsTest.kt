package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory

class BookSearchMigrationsTest {

    private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
    }

    @Test
    fun shouldCreateBooksTableWithAllColumns() {
        val columns = fetchTableColumns("books")
        val columnNames = columns.map { it["name"] as String }

        val expectedColumns = listOf(
            "md5", "title", "author", "language", "format", "file_size",
            "source_url", "detail_url", "cover_url", "publisher", "year",
            "description", "indexed_at"
        )

        for (expected in expectedColumns) {
            assertTrue(columnNames.contains(expected), "books table should have column '$expected'")
        }

        assertFalse(columnNames.contains("download_url_fast"), "books table should NOT have download_url_fast column")
        assertFalse(columnNames.contains("download_url_slow"), "books table should NOT have download_url_slow column")

        val md5Column = columns.first { it["name"] == "md5" }
        assertEquals(1, md5Column["pk"], "md5 should be the primary key")

        val titleColumn = columns.first { it["name"] == "title" }
        assertEquals(1, titleColumn["notnull"], "title should be NOT NULL")

        val indexedAtColumn = columns.first { it["name"] == "indexed_at" }
        assertEquals(1, indexedAtColumn["notnull"], "indexed_at should be NOT NULL")
    }

    @Test
    fun shouldCreateUserLibraryTableWithUniqueConstraintAndForeignKeys() {
        val columns = fetchTableColumns("user_library")
        val columnNames = columns.map { it["name"] as String }

        val expectedColumns = listOf("id", "user_id", "book_md5", "format", "file_path", "added_at")
        for (expected in expectedColumns) {
            assertTrue(columnNames.contains(expected), "user_library table should have column '$expected'")
        }

        val userIdCol = columns.first { it["name"] == "user_id" }
        assertEquals(1, userIdCol["notnull"], "user_id should be NOT NULL")

        val bookMd5Col = columns.first { it["name"] == "book_md5" }
        assertEquals(1, bookMd5Col["notnull"], "book_md5 should be NOT NULL")

        val formatCol = columns.first { it["name"] == "format" }
        assertEquals(1, formatCol["notnull"], "format should be NOT NULL")

        val indexes = fetchTableIndexes("user_library")
        val uniqueIndexColumns = indexes
            .filter { it["unique"] == 1 }
            .map { it["name"] as String }

        assertTrue(
            uniqueIndexColumns.any { indexName ->
                val indexColumns = fetchIndexColumns(indexName)
                indexColumns.containsAll(listOf("user_id", "book_md5", "format"))
            },
            "user_library should have UNIQUE constraint on (user_id, book_md5, format)"
        )
    }

    @Test
    fun shouldCreateMirrorsTableWithUniqueDomainConstraint() {
        val columns = fetchTableColumns("mirrors")
        val columnNames = columns.map { it["name"] as String }

        val expectedColumns = listOf("id", "domain", "base_url", "is_working", "last_checked_at", "response_time_ms")
        for (expected in expectedColumns) {
            assertTrue(columnNames.contains(expected), "mirrors table should have column '$expected'")
        }

        val domainCol = columns.first { it["name"] == "domain" }
        assertEquals(1, domainCol["notnull"], "domain should be NOT NULL")

        val baseUrlCol = columns.first { it["name"] == "base_url" }
        assertEquals(1, baseUrlCol["notnull"], "base_url should be NOT NULL")

        val indexes = fetchTableIndexes("mirrors")
        val uniqueIndexColumns = indexes
            .filter { it["unique"] == 1 }
            .map { it["name"] as String }

        assertTrue(
            uniqueIndexColumns.any { indexName ->
                val cols = fetchIndexColumns(indexName)
                cols.contains("domain")
            },
            "mirrors should have UNIQUE constraint on domain"
        )
    }

    @Test
    fun shouldEnforceForeignKeysOnUserLibrary() {
        val exception = assertThrows(Exception::class.java) {
            dsl.execute(
                """INSERT INTO user_library (user_id, book_md5, format, added_at)
                   VALUES (9999, 'nonexistent_md5', 'epub', '2026-01-01T00:00:00Z')"""
            )
        }
        assertNotNull(exception, "Inserting with non-existent user_id should fail due to FK constraint")
    }

    private fun fetchTableColumns(tableName: String): List<Map<String, Any?>> {
        val result = dsl.fetch("PRAGMA table_info($tableName)")
        return result.map { record ->
            mapOf(
                "name" to record.get("name", String::class.java),
                "type" to record.get("type", String::class.java),
                "notnull" to record.get("notnull", Int::class.java),
                "pk" to record.get("pk", Int::class.java)
            )
        }
    }

    private fun fetchTableIndexes(tableName: String): List<Map<String, Any?>> {
        val result = dsl.fetch("PRAGMA index_list($tableName)")
        return result.map { record ->
            mapOf(
                "name" to record.get("name", String::class.java),
                "unique" to record.get("unique", Int::class.java)
            )
        }
    }

    private fun fetchIndexColumns(indexName: String): List<String> {
        val result = dsl.fetch("PRAGMA index_info($indexName)")
        return result.map { it.get("name", String::class.java)!! }
    }
}
