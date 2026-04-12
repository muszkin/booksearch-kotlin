package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.jooq.generated.tables.references.USERS
import java.time.Instant
import java.time.temporal.ChronoUnit

class DatabaseLayerTest {

    private lateinit var dsl: DSLContext
    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository
    private lateinit var systemConfigRepository: SystemConfigRepository

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        userRepository = UserRepository(dsl)
        refreshTokenRepository = RefreshTokenRepository(dsl)
        passwordResetTokenRepository = PasswordResetTokenRepository(dsl)
        systemConfigRepository = SystemConfigRepository(dsl)
    }

    @Nested
    inner class DatabaseFactoryTest {

        @Test
        fun shouldCreateDslContextWithSqliteDialectAndRunMigrations() {
            assertEquals(SQLDialect.SQLITE, dsl.dialect())

            val userCount = dsl.selectCount().from(USERS).fetchOne(0, Int::class.java)
            assertNotNull(userCount)
            assertEquals(0, userCount)
        }
    }

    @Nested
    inner class UserRepositoryTest {

        @Test
        fun shouldCreateUserAndFindByEmail() {
            val created = userRepository.create(
                email = "test@example.com",
                passwordHash = "hashed123",
                displayName = "Test User",
                isSuperAdmin = false,
                forcePasswordChange = false
            )

            assertNotNull(created.id)
            assertEquals("test@example.com", created.email)
            assertEquals("Test User", created.displayName)

            val found = userRepository.findByEmail("test@example.com")
            assertNotNull(found)
            assertEquals(created.id, found!!.id)
            assertEquals("test@example.com", found.email)
        }

        @Test
        fun shouldFindUserById() {
            val created = userRepository.create(
                email = "findme@example.com",
                passwordHash = "hash",
                displayName = "Find Me",
                isSuperAdmin = true,
                forcePasswordChange = false
            )

            val found = userRepository.findById(created.id!!)
            assertNotNull(found)
            assertEquals("findme@example.com", found!!.email)
            assertEquals(1, found.isSuperAdmin)
        }

        @Test
        fun shouldReturnNullForNonExistentEmail() {
            val found = userRepository.findByEmail("nonexistent@example.com")
            assertNull(found)
        }
    }

    @Nested
    inner class RefreshTokenRepositoryTest {

        @Test
        fun shouldCreateTokenAndFindValidByToken() {
            val user = userRepository.create("user@test.com", "hash", "User", false, false)
            val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString()

            refreshTokenRepository.create(user.id!!, "refresh-token-abc", expiresAt)

            val found = refreshTokenRepository.findValidByToken("refresh-token-abc")
            assertNotNull(found)
            assertEquals(user.id, found!!.userId)
            assertEquals("refresh-token-abc", found.token)
        }

        @Test
        fun shouldNotFindRevokedToken() {
            val user = userRepository.create("user2@test.com", "hash", "User2", false, false)
            val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString()

            refreshTokenRepository.create(user.id!!, "revoke-me", expiresAt)
            refreshTokenRepository.revokeByToken("revoke-me")

            val found = refreshTokenRepository.findValidByToken("revoke-me")
            assertNull(found)
        }

        @Test
        fun shouldRevokeAllTokensByUserId() {
            val user = userRepository.create("user3@test.com", "hash", "User3", false, false)
            val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString()

            refreshTokenRepository.create(user.id!!, "token-1", expiresAt)
            refreshTokenRepository.create(user.id!!, "token-2", expiresAt)

            refreshTokenRepository.revokeAllByUserId(user.id!!)

            assertNull(refreshTokenRepository.findValidByToken("token-1"))
            assertNull(refreshTokenRepository.findValidByToken("token-2"))
        }
    }

    @Nested
    inner class PasswordResetTokenRepositoryTest {

        @Test
        fun shouldCreateTokenAndFindValid() {
            val user = userRepository.create("reset@test.com", "hash", "Reset", false, false)
            val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString()

            passwordResetTokenRepository.create(user.id!!, "reset-token-xyz", expiresAt)

            val found = passwordResetTokenRepository.findValidByToken("reset-token-xyz")
            assertNotNull(found)
            assertEquals(user.id, found!!.userId)
        }

        @Test
        fun shouldNotFindUsedToken() {
            val user = userRepository.create("used@test.com", "hash", "Used", false, false)
            val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString()

            passwordResetTokenRepository.create(user.id!!, "used-token", expiresAt)
            passwordResetTokenRepository.markAsUsed("used-token")

            val found = passwordResetTokenRepository.findValidByToken("used-token")
            assertNull(found)
        }
    }

    @Nested
    inner class SystemConfigRepositoryTest {

        @Test
        fun shouldHaveRegistrationEnabledByDefault() {
            assertTrue(systemConfigRepository.isRegistrationEnabled())
        }

        @Test
        fun shouldSetAndGetValue() {
            systemConfigRepository.setValue("test_key", "test_value")
            assertEquals("test_value", systemConfigRepository.getValue("test_key"))
        }

        @Test
        fun shouldToggleRegistrationEnabled() {
            systemConfigRepository.setRegistrationEnabled(false)
            assertFalse(systemConfigRepository.isRegistrationEnabled())

            systemConfigRepository.setRegistrationEnabled(true)
            assertTrue(systemConfigRepository.isRegistrationEnabled())
        }
    }
}
