package pl.fairydeck.booksearch.service

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.api.AuthenticationException
import pl.fairydeck.booksearch.api.ConflictException
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.repository.PasswordResetTokenRepository
import pl.fairydeck.booksearch.repository.RefreshTokenRepository
import pl.fairydeck.booksearch.repository.SystemConfigRepository
import pl.fairydeck.booksearch.repository.UserRepository

class AuthServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var authService: AuthService

    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository
    private lateinit var systemConfigRepository: SystemConfigRepository

    companion object {
        private const val JWT_SECRET = "test-secret-key-that-is-long-enough-for-hmac256"
        private const val JWT_ISSUER = "booksearch-test"
        private const val JWT_AUDIENCE = "booksearch-test-audience"
        private const val ACCESS_TOKEN_EXPIRATION_MS = 3_600_000L
        private const val REFRESH_TOKEN_EXPIRATION_MS = 2_592_000_000L
    }

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        userRepository = UserRepository(dsl)
        refreshTokenRepository = RefreshTokenRepository(dsl)
        passwordResetTokenRepository = PasswordResetTokenRepository(dsl)
        systemConfigRepository = SystemConfigRepository(dsl)

        authService = AuthService(
            dsl = dsl,
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            passwordResetTokenRepository = passwordResetTokenRepository,
            systemConfigRepository = systemConfigRepository,
            jwtSecret = JWT_SECRET,
            jwtIssuer = JWT_ISSUER,
            jwtAudience = JWT_AUDIENCE,
            accessTokenExpirationMs = ACCESS_TOKEN_EXPIRATION_MS,
            refreshTokenExpirationMs = REFRESH_TOKEN_EXPIRATION_MS
        )
    }

    @Test
    fun registerFirstUserBecomesSuperAdmin() {
        val response = authService.register("admin@example.com", "password123", "Admin User")

        assertTrue(response.user.isSuperAdmin)
        assertEquals("admin@example.com", response.user.email)
        assertEquals("Admin User", response.user.displayName)
        assertTrue(response.accessToken.isNotBlank())
        assertTrue(response.refreshToken.isNotBlank())
    }

    @Test
    fun registerSubsequentUserIsRegularUser() {
        authService.register("first@example.com", "password123", "First User")
        val response = authService.register("second@example.com", "password456", "Second User")

        assertFalse(response.user.isSuperAdmin)
        assertEquals("second@example.com", response.user.email)
    }

    @Test
    fun registerFailsWhenRegistrationDisabled() {
        authService.register("admin@example.com", "password123", "Admin")
        systemConfigRepository.setRegistrationEnabled(false)

        val exception = assertThrows(AuthenticationException::class.java) {
            authService.register("new@example.com", "password123", "New User")
        }
        assertTrue(exception.message!!.contains("Registration"))
    }

    @Test
    fun registerFailsWithDuplicateEmail() {
        authService.register("duplicate@example.com", "password123", "First")

        assertThrows(ConflictException::class.java) {
            authService.register("duplicate@example.com", "password456", "Second")
        }
    }

    @Test
    fun loginWithValidCredentialsReturnsTokens() {
        authService.register("user@example.com", "correctpassword", "User")

        val response = authService.login("user@example.com", "correctpassword")

        assertEquals("user@example.com", response.user.email)
        assertTrue(response.accessToken.isNotBlank())
        assertTrue(response.refreshToken.isNotBlank())
    }

    @Test
    fun loginWithInvalidCredentialsThrowsAuthenticationException() {
        authService.register("user@example.com", "correctpassword", "User")

        assertThrows(AuthenticationException::class.java) {
            authService.login("user@example.com", "wrongpassword")
        }

        assertThrows(AuthenticationException::class.java) {
            authService.login("nonexistent@example.com", "password")
        }
    }

    @Test
    fun refreshWithValidTokenReturnsNewAccessToken() {
        val loginResponse = authService.register("user@example.com", "password123", "User")

        val refreshResponse = authService.refresh(loginResponse.refreshToken)

        assertTrue(refreshResponse.accessToken.isNotBlank())
    }

    @Test
    fun refreshWithRevokedTokenThrowsAuthenticationException() {
        val loginResponse = authService.register("user@example.com", "password123", "User")
        authService.logout(loginResponse.refreshToken)

        assertThrows(AuthenticationException::class.java) {
            authService.refresh(loginResponse.refreshToken)
        }
    }

    @Test
    fun refreshRotatesRefreshTokenAndReturnsUser() {
        val loginResponse = authService.register("user@example.com", "password123", "User")
        val oldToken = loginResponse.refreshToken

        val response = authService.refresh(oldToken)

        assertNotEquals(oldToken, response.refreshToken)
        assertTrue(response.refreshToken.isNotBlank())
        assertTrue(response.accessToken.isNotBlank())
        assertEquals("user@example.com", response.user.email)
    }

    @Test
    fun refreshInvalidatesOldRefreshToken() {
        val loginResponse = authService.register("user@example.com", "password123", "User")
        val oldToken = loginResponse.refreshToken

        authService.refresh(oldToken)

        val exception = assertThrows(AuthenticationException::class.java) {
            authService.refresh(oldToken)
        }
        assertEquals("Invalid or expired refresh token", exception.message)
    }

    @Test
    fun getCurrentUserReturnsUserResponseForActiveUser() {
        val loginResponse = authService.register("user@example.com", "password123", "User")

        val response = authService.getCurrentUser(loginResponse.user.id.toInt())

        assertEquals("user@example.com", response.email)
        assertEquals("User", response.displayName)
        assertTrue(response.isActive)
    }
}
