package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory

class ActivityAndRequestLogsTest {

    private lateinit var dsl: DSLContext

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
    }

    @Nested
    inner class ActivityLogsSchemaTest {

        @Test
        fun shouldCreateActivityLogsTableWithAllColumnsAndIndexes() {
            val columns = fetchTableColumns("activity_logs")
            val columnNames = columns.map { it["name"] as String }

            val expectedColumns = listOf(
                "id", "user_id", "action_type", "entity_type",
                "entity_id", "details", "created_at"
            )

            for (expected in expectedColumns) {
                assertTrue(columnNames.contains(expected), "activity_logs table should have column '$expected'")
            }

            val idColumn = columns.first { it["name"] == "id" }
            assertEquals(1, idColumn["pk"], "id should be the primary key")

            val userIdCol = columns.first { it["name"] == "user_id" }
            assertEquals(1, userIdCol["notnull"], "user_id should be NOT NULL")

            val actionTypeCol = columns.first { it["name"] == "action_type" }
            assertEquals(1, actionTypeCol["notnull"], "action_type should be NOT NULL")

            val entityTypeCol = columns.first { it["name"] == "entity_type" }
            assertEquals(1, entityTypeCol["notnull"], "entity_type should be NOT NULL")

            val createdAtCol = columns.first { it["name"] == "created_at" }
            assertEquals(1, createdAtCol["notnull"], "created_at should be NOT NULL")

            val entityIdCol = columns.first { it["name"] == "entity_id" }
            assertEquals(0, entityIdCol["notnull"], "entity_id should be nullable")

            val detailsCol = columns.first { it["name"] == "details" }
            assertEquals(0, detailsCol["notnull"], "details should be nullable")

            val indexes = fetchTableIndexes("activity_logs")
            val indexNames = indexes.map { it["name"] as String }

            assertTrue(
                indexNames.any { indexName ->
                    val cols = fetchIndexColumns(indexName)
                    cols.containsAll(listOf("user_id", "created_at"))
                },
                "activity_logs should have index on (user_id, created_at)"
            )
        }

        @Test
        fun shouldEnforceForeignKeyFromActivityLogsToUsers() {
            val exception = assertThrows(Exception::class.java) {
                dsl.execute(
                    """INSERT INTO activity_logs (user_id, action_type, entity_type, created_at)
                       VALUES (9999, 'login', 'user', '2026-01-01T00:00:00Z')"""
                )
            }
            assertNotNull(exception, "Inserting with non-existent user_id should fail due to FK constraint")
        }
    }

    @Nested
    inner class RequestLogsSchemaTest {

        @Test
        fun shouldCreateRequestLogsTableWithAllColumnsAndIndexes() {
            val columns = fetchTableColumns("request_logs")
            val columnNames = columns.map { it["name"] as String }

            val expectedColumns = listOf(
                "id", "method", "path", "status_code", "duration_ms",
                "request_headers", "response_headers", "request_id",
                "user_id", "created_at"
            )

            for (expected in expectedColumns) {
                assertTrue(columnNames.contains(expected), "request_logs table should have column '$expected'")
            }

            val idColumn = columns.first { it["name"] == "id" }
            assertEquals(1, idColumn["pk"], "id should be the primary key")

            val methodCol = columns.first { it["name"] == "method" }
            assertEquals(1, methodCol["notnull"], "method should be NOT NULL")

            val pathCol = columns.first { it["name"] == "path" }
            assertEquals(1, pathCol["notnull"], "path should be NOT NULL")

            val statusCodeCol = columns.first { it["name"] == "status_code" }
            assertEquals(1, statusCodeCol["notnull"], "status_code should be NOT NULL")

            val durationMsCol = columns.first { it["name"] == "duration_ms" }
            assertEquals(1, durationMsCol["notnull"], "duration_ms should be NOT NULL")

            val createdAtCol = columns.first { it["name"] == "created_at" }
            assertEquals(1, createdAtCol["notnull"], "created_at should be NOT NULL")

            val userIdCol = columns.first { it["name"] == "user_id" }
            assertEquals(0, userIdCol["notnull"], "user_id should be nullable")

            val requestHeadersCol = columns.first { it["name"] == "request_headers" }
            assertEquals(0, requestHeadersCol["notnull"], "request_headers should be nullable")

            val indexes = fetchTableIndexes("request_logs")
            val indexNames = indexes.map { it["name"] as String }

            assertTrue(
                indexNames.any { indexName ->
                    val cols = fetchIndexColumns(indexName)
                    cols.contains("created_at")
                },
                "request_logs should have index on created_at"
            )

            assertTrue(
                indexNames.any { indexName ->
                    val cols = fetchIndexColumns(indexName)
                    cols.containsAll(listOf("method", "path"))
                },
                "request_logs should have index on (method, path)"
            )
        }
    }

    @Nested
    inner class ActivityLogRepositoryTest {

        private lateinit var activityLogRepository: ActivityLogRepository
        private lateinit var userRepository: UserRepository

        @BeforeEach
        fun setUp() {
            userRepository = UserRepository(dsl)
            activityLogRepository = ActivityLogRepository(dsl)
        }

        @Test
        fun shouldInsertAndFindByUserIdWithPagination() {
            val user = userRepository.create("activity@test.com", "hash", "Activity User", false, false)
            val userId = user.id!!

            for (i in 1..5) {
                activityLogRepository.insert(userId, "action_$i", "entity_$i", "id_$i", """{"key":"value_$i"}""")
            }

            val firstPage = activityLogRepository.findByUserId(userId, page = 1, pageSize = 2)
            assertEquals(5, firstPage.totalCount)
            assertEquals(2, firstPage.items.size)

            val secondPage = activityLogRepository.findByUserId(userId, page = 2, pageSize = 2)
            assertEquals(5, secondPage.totalCount)
            assertEquals(2, secondPage.items.size)

            val thirdPage = activityLogRepository.findByUserId(userId, page = 3, pageSize = 2)
            assertEquals(1, thirdPage.items.size)

            val filtered = activityLogRepository.findByUserId(userId, page = 1, pageSize = 10, actionType = "action_3")
            assertEquals(1, filtered.totalCount)
            assertEquals("action_3", filtered.items.first().actionType)
        }
    }

    @Nested
    inner class RequestLogRepositoryTest {

        private lateinit var requestLogRepository: RequestLogRepository

        @BeforeEach
        fun setUp() {
            requestLogRepository = RequestLogRepository(dsl)
        }

        @Test
        fun shouldInsertAndFindAllWithFilters() {
            requestLogRepository.insert("GET", "/api/books", 200, 50, null, null, "req-1", null)
            requestLogRepository.insert("POST", "/api/auth/login", 200, 120, null, null, "req-2", null)
            requestLogRepository.insert("GET", "/api/books", 404, 30, null, null, "req-3", null)
            requestLogRepository.insert("DELETE", "/api/books/1", 204, 45, null, null, "req-4", null)

            val all = requestLogRepository.findAll(page = 1, pageSize = 10)
            assertEquals(4, all.totalCount)
            assertEquals(4, all.items.size)

            val getOnly = requestLogRepository.findAll(page = 1, pageSize = 10, method = "GET")
            assertEquals(2, getOnly.totalCount)

            val booksPath = requestLogRepository.findAll(page = 1, pageSize = 10, path = "/api/books")
            assertEquals(2, booksPath.totalCount)

            val notFound = requestLogRepository.findAll(page = 1, pageSize = 10, statusCode = 404)
            assertEquals(1, notFound.totalCount)

            val paginated = requestLogRepository.findAll(page = 1, pageSize = 2)
            assertEquals(4, paginated.totalCount)
            assertEquals(2, paginated.items.size)
        }
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
