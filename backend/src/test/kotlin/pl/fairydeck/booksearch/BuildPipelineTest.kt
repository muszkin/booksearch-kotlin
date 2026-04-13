package pl.fairydeck.booksearch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.sql.DriverManager

class BuildPipelineTest {

    @Test
    fun shouldGenerateOpenApiModelClassesWithSerializableAnnotation() {
        val modelClasses = listOf(
            "pl.fairydeck.booksearch.models.RegisterRequest",
            "pl.fairydeck.booksearch.models.LoginRequest",
            "pl.fairydeck.booksearch.models.LoginResponse",
            "pl.fairydeck.booksearch.models.RefreshRequest",
            "pl.fairydeck.booksearch.models.RefreshResponse",
            "pl.fairydeck.booksearch.models.ChangeOwnPasswordRequest",
            "pl.fairydeck.booksearch.models.LogoutRequest",
            "pl.fairydeck.booksearch.models.ToggleRegistrationRequest",
            "pl.fairydeck.booksearch.models.CreateUserRequest",
            "pl.fairydeck.booksearch.models.ChangePasswordRequest",
            "pl.fairydeck.booksearch.models.UserResponse"
        )

        for (className in modelClasses) {
            val clazz = Class.forName(className)
            val hasSerializable = clazz.annotations.any {
                it.annotationClass.qualifiedName == "kotlinx.serialization.Serializable"
            }
            assertTrue(hasSerializable, "$className should have @Serializable annotation")
        }
    }

    @Test
    fun shouldGenerateJooqTableClassesForAllRequiredTables() {
        val tableClasses = listOf(
            "pl.fairydeck.booksearch.jooq.generated.tables.Users",
            "pl.fairydeck.booksearch.jooq.generated.tables.RefreshTokens",
            "pl.fairydeck.booksearch.jooq.generated.tables.PasswordResetTokens",
            "pl.fairydeck.booksearch.jooq.generated.tables.SystemConfig",
            "pl.fairydeck.booksearch.jooq.generated.tables.Books",
            "pl.fairydeck.booksearch.jooq.generated.tables.UserLibrary",
            "pl.fairydeck.booksearch.jooq.generated.tables.Mirrors"
        )

        for (className in tableClasses) {
            val clazz = assertDoesNotThrow({ Class.forName(className) },
                "JOOQ table class $className should exist")
            assertNotNull(clazz, "JOOQ table class $className should be loadable")
        }
    }

    @Test
    fun shouldApplyLiquibaseChangelogCleanlyToInMemorySqlite() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val database = liquibase.database.DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(
                    liquibase.database.jvm.JdbcConnection(connection)
                )

            val liquibase = liquibase.Liquibase(
                "db/changelog/changelog.yml",
                liquibase.resource.ClassLoaderResourceAccessor(),
                database
            )

            assertDoesNotThrow({ liquibase.update("") },
                "Liquibase changelog should apply cleanly to in-memory SQLite")

            val statement = connection.createStatement()
            val tables = mutableListOf<String>()
            val rs = statement.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'DATABASECHANGELOG%'"
            )
            while (rs.next()) {
                tables.add(rs.getString("name"))
            }

            assertTrue(tables.contains("users"), "users table should exist")
            assertTrue(tables.contains("refresh_tokens"), "refresh_tokens table should exist")
            assertTrue(tables.contains("password_reset_tokens"), "password_reset_tokens table should exist")
            assertTrue(tables.contains("system_config"), "system_config table should exist")
            assertTrue(tables.contains("books"), "books table should exist")
            assertTrue(tables.contains("user_library"), "user_library table should exist")
            assertTrue(tables.contains("mirrors"), "mirrors table should exist")

            val configRs = statement.executeQuery(
                "SELECT value FROM system_config WHERE key = 'registration_enabled'"
            )
            assertTrue(configRs.next(), "system_config should have registration_enabled seed")
            assertEquals("true", configRs.getString("value"))
        }
    }
}
