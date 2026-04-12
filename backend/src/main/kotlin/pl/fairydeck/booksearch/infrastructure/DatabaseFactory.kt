package pl.fairydeck.booksearch.infrastructure

import liquibase.Liquibase
import liquibase.database.DatabaseFactory as LiquibaseDatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.sqlite.SQLiteDataSource
import java.sql.Connection

object DatabaseFactory {

    private const val CHANGELOG_PATH = "db/changelog/changelog.yml"
    private const val FOREIGN_KEYS_PRAGMA = "PRAGMA foreign_keys = ON"

    fun create(databasePath: String): DSLContext {
        val dataSource = SQLiteDataSource().apply {
            url = "jdbc:sqlite:$databasePath"
        }
        runMigrations(dataSource)
        enableForeignKeysOnDataSource(dataSource)
        return DSL.using(dataSource, SQLDialect.SQLITE)
    }

    fun createInMemory(): DSLContext {
        val connection = SQLiteDataSource().apply {
            url = "jdbc:sqlite::memory:"
        }.connection
        enableForeignKeys(connection)
        runMigrationsOnConnection(connection)
        return DSL.using(connection, SQLDialect.SQLITE)
    }

    private fun enableForeignKeys(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(FOREIGN_KEYS_PRAGMA)
        }
    }

    private fun enableForeignKeysOnDataSource(dataSource: SQLiteDataSource) {
        dataSource.connection.use { connection ->
            enableForeignKeys(connection)
        }
    }

    private fun runMigrations(dataSource: SQLiteDataSource) {
        dataSource.connection.use { migrationConnection ->
            val database = LiquibaseDatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(JdbcConnection(migrationConnection))
            Liquibase(CHANGELOG_PATH, ClassLoaderResourceAccessor(), database).use { liquibase ->
                liquibase.update("")
            }
        }
    }

    private fun runMigrationsOnConnection(connection: Connection) {
        val jdbcConnection = JdbcConnection(connection)
        val database = LiquibaseDatabaseFactory.getInstance()
            .findCorrectDatabaseImplementation(jdbcConnection)
        val liquibase = Liquibase(CHANGELOG_PATH, ClassLoaderResourceAccessor(), database)
        liquibase.update("")
    }
}
