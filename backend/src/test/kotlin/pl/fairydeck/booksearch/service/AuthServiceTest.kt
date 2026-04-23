package pl.fairydeck.booksearch.service

import com.auth0.jwt.JWT
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import pl.fairydeck.booksearch.api.AuthenticationException
import pl.fairydeck.booksearch.api.AuthorizationException
import pl.fairydeck.booksearch.api.ConflictException
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.infrastructure.DatabaseFactory
import pl.fairydeck.booksearch.jooq.generated.tables.references.ACTIVITY_LOGS
import pl.fairydeck.booksearch.jooq.generated.tables.references.REFRESH_TOKENS
import pl.fairydeck.booksearch.jooq.generated.tables.references.USERS
import pl.fairydeck.booksearch.repository.ActivityLogRepository
import pl.fairydeck.booksearch.repository.PasswordResetTokenRepository
import pl.fairydeck.booksearch.repository.RefreshTokenRepository
import pl.fairydeck.booksearch.repository.SystemConfigRepository
import pl.fairydeck.booksearch.repository.UserRepository
import java.time.Instant

class AuthServiceTest {

    private lateinit var dsl: DSLContext
    private lateinit var authService: AuthService

    private lateinit var userRepository: UserRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository
    private lateinit var systemConfigRepository: SystemConfigRepository
    private lateinit var activityLogRepository: ActivityLogRepository
    private lateinit var activityLogService: ActivityLogService

    companion object {
        private const val JWT_SECRET = "test-secret-key-that-is-long-enough-for-hmac256"
        private const val JWT_ISSUER = "booksearch-test"
        private const val JWT_AUDIENCE = "booksearch-test-audience"
        private const val ACCESS_TOKEN_EXPIRATION_MS = 3_600_000L
        private const val REFRESH_TOKEN_EXPIRATION_MS = 2_592_000_000L
        private const val IMPERSONATION_ACCESS_TTL_MS = 30L * 60L * 1000L
        private const val IMPERSONATION_REFRESH_TTL_MS = 60L * 60L * 1000L
    }

    @BeforeEach
    fun setUp() {
        dsl = DatabaseFactory.createInMemory()
        userRepository = UserRepository(dsl)
        refreshTokenRepository = RefreshTokenRepository(dsl)
        passwordResetTokenRepository = PasswordResetTokenRepository(dsl)
        systemConfigRepository = SystemConfigRepository(dsl)
        activityLogRepository = ActivityLogRepository(dsl)
        activityLogService = ActivityLogService(activityLogRepository)

        authService = AuthService(
            dsl = dsl,
            userRepository = userRepository,
            refreshTokenRepository = refreshTokenRepository,
            passwordResetTokenRepository = passwordResetTokenRepository,
            systemConfigRepository = systemConfigRepository,
            activityLogService = activityLogService,
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
        val principal = pl.fairydeck.booksearch.api.UserPrincipal(
            userId = loginResponse.user.id.toInt(),
            email = loginResponse.user.email,
            isSuperAdmin = loginResponse.user.isSuperAdmin
        )

        val response = authService.getCurrentUser(principal)

        assertEquals("user@example.com", response.email)
        assertEquals("User", response.displayName)
        assertTrue(response.isActive)
    }

    // --- Impersonation tests (Task Group 3) ---

    @Test
    fun startImpersonationMintsTokensWithActClaimsAndDualLogs() {
        val adminResponse = authService.register("admin@example.com", "adminpass123", "Admin")
        val targetResponse = authService.register("target@example.com", "targetpass123", "Target User")
        val adminId = adminResponse.user.id.toInt()
        val targetId = targetResponse.user.id.toInt()
        val adminEmail = adminResponse.user.email

        val beforeStart = Instant.now()
        val response = authService.startImpersonation(adminId, targetId)
        val afterStart = Instant.now()

        // LoginResponse shape
        assertTrue(response.accessToken.isNotBlank())
        assertTrue(response.refreshToken.isNotBlank())
        assertEquals(targetId.toLong(), response.user.id)
        assertEquals(adminId.toLong(), response.user.actAsUserId)
        assertEquals(adminEmail, response.user.actAsEmail)

        // Decode access JWT and inspect claims
        val decoded = JWT.decode(response.accessToken)
        assertEquals(targetId.toString(), decoded.subject)
        assertEquals("target@example.com", decoded.getClaim("email").asString())
        assertEquals(false, decoded.getClaim("is_super_admin").asBoolean())
        assertEquals(adminId, decoded.getClaim("original_admin_id").asInt())
        assertEquals(adminEmail, decoded.getClaim("act_email").asString())
        // Critical (audit IM2): act_sub MUST NOT be emitted
        assertTrue(
            decoded.getClaim("act_sub").isMissing || decoded.getClaim("act_sub").isNull,
            "act_sub claim must be absent per audit IM2"
        )
        // TTL ~ 30 minutes (with a few seconds of slack)
        val ttlMs = decoded.expiresAt.time - decoded.issuedAt.time
        val ttlDeltaMs = Math.abs(ttlMs - IMPERSONATION_ACCESS_TTL_MS)
        assertTrue(ttlDeltaMs < 5_000L, "access ttl delta too large: ${ttlDeltaMs}ms")

        // Refresh token is owned by the admin (for clean revoke on stop)
        val refreshRow = dsl.selectFrom(REFRESH_TOKENS)
            .where(REFRESH_TOKENS.TOKEN.eq(response.refreshToken))
            .fetchOne()
        assertNotNull(refreshRow)
        assertEquals(adminId, refreshRow!!.userId)
        assertEquals(0, refreshRow.revoked)
        // expires_at within 1h ± 5s
        val refreshExpires = Instant.parse(refreshRow.expiresAt)
        val expectedMin = beforeStart.plusMillis(IMPERSONATION_REFRESH_TTL_MS - 5_000L)
        val expectedMax = afterStart.plusMillis(IMPERSONATION_REFRESH_TTL_MS + 5_000L)
        assertTrue(
            refreshExpires.isAfter(expectedMin) && refreshExpires.isBefore(expectedMax),
            "refresh expires_at out of range: $refreshExpires (expected between $expectedMin and $expectedMax)"
        )

        // activity_logs: two rows for IMPERSONATION_START
        val adminLogs = dsl.selectFrom(ACTIVITY_LOGS)
            .where(ACTIVITY_LOGS.USER_ID.eq(adminId))
            .and(ACTIVITY_LOGS.ACTION_TYPE.eq("IMPERSONATION_START"))
            .fetch()
        val targetLogs = dsl.selectFrom(ACTIVITY_LOGS)
            .where(ACTIVITY_LOGS.USER_ID.eq(targetId))
            .and(ACTIVITY_LOGS.ACTION_TYPE.eq("IMPERSONATION_START"))
            .fetch()
        assertEquals(1, adminLogs.size, "expected 1 admin-perspective START log row")
        assertEquals(1, targetLogs.size, "expected 1 target-perspective START log row")
        assertTrue(
            adminLogs[0].details!!.contains("\"target_user_id\"") &&
                adminLogs[0].details!!.contains(targetId.toString()),
            "admin log details should include target_user_id=$targetId; got: ${adminLogs[0].details}"
        )
        assertTrue(
            targetLogs[0].details!!.contains("\"impersonated_by_admin_id\"") &&
                targetLogs[0].details!!.contains(adminId.toString()),
            "target log details should include impersonated_by_admin_id=$adminId; got: ${targetLogs[0].details}"
        )
    }

    @Test
    fun startImpersonationRejectsSelf() {
        val adminResponse = authService.register("admin@example.com", "adminpass123", "Admin")
        val adminId = adminResponse.user.id.toInt()

        val exception = assertThrows(ValidationException::class.java) {
            authService.startImpersonation(adminId, adminId)
        }
        assertEquals("Cannot impersonate yourself", exception.message)
    }

    @Test
    fun startImpersonationRejectsSuperAdminTarget() {
        val admin1Response = authService.register("admin1@example.com", "adminpass123", "Admin One")
        val admin2Response = authService.register("admin2@example.com", "adminpass123", "Admin Two")
        val admin1Id = admin1Response.user.id.toInt()
        val admin2Id = admin2Response.user.id.toInt()

        // Promote admin2 to super admin directly in DB (no repository method for this).
        dsl.update(USERS)
            .set(USERS.IS_SUPER_ADMIN, 1)
            .where(USERS.ID.eq(admin2Id))
            .execute()

        val exception = assertThrows(AuthorizationException::class.java) {
            authService.startImpersonation(admin1Id, admin2Id)
        }
        assertEquals("Cannot impersonate another super admin", exception.message)
    }

    @Test
    fun startImpersonationRejectsInactiveTarget() {
        val adminResponse = authService.register("admin@example.com", "adminpass123", "Admin")
        val targetResponse = authService.register("target@example.com", "targetpass123", "Target User")
        val adminId = adminResponse.user.id.toInt()
        val targetId = targetResponse.user.id.toInt()

        // Deactivate target directly (no repository method exposed).
        dsl.update(USERS)
            .set(USERS.IS_ACTIVE, 0)
            .where(USERS.ID.eq(targetId))
            .execute()

        val exception = assertThrows(ValidationException::class.java) {
            authService.startImpersonation(adminId, targetId)
        }
        assertEquals("Cannot impersonate an inactive user", exception.message)
    }

    @Test
    fun stopImpersonationReturnsAdminTokensAndDualLogs() {
        val adminResponse = authService.register("admin@example.com", "adminpass123", "Admin")
        val targetResponse = authService.register("target@example.com", "targetpass123", "Target User")
        val adminId = adminResponse.user.id.toInt()
        val targetId = targetResponse.user.id.toInt()

        val impersonationResponse = authService.startImpersonation(adminId, targetId)
        val impersonationRefreshToken = impersonationResponse.refreshToken

        val stopResponse = authService.stopImpersonation(
            currentRefreshToken = impersonationRefreshToken,
            originalAdminId = adminId,
            impersonatedUserId = targetId
        )

        // Returned LoginResponse is now admin's session (no actAs* fields).
        assertEquals(adminId.toLong(), stopResponse.user.id)
        assertNull(stopResponse.user.actAsUserId)
        assertNull(stopResponse.user.actAsEmail)
        assertTrue(stopResponse.accessToken.isNotBlank())
        assertTrue(stopResponse.refreshToken.isNotBlank())
        assertNotEquals(impersonationRefreshToken, stopResponse.refreshToken)

        // Impersonation refresh token must be revoked.
        val revokedRow = dsl.selectFrom(REFRESH_TOKENS)
            .where(REFRESH_TOKENS.TOKEN.eq(impersonationRefreshToken))
            .fetchOne()
        assertNotNull(revokedRow)
        assertEquals(1, revokedRow!!.revoked)

        // Fresh admin refresh token exists and is owned by the admin.
        val newRefreshRow = dsl.selectFrom(REFRESH_TOKENS)
            .where(REFRESH_TOKENS.TOKEN.eq(stopResponse.refreshToken))
            .fetchOne()
        assertNotNull(newRefreshRow)
        assertEquals(adminId, newRefreshRow!!.userId)
        assertEquals(0, newRefreshRow.revoked)

        // Access token has no impersonation claims.
        val decoded = JWT.decode(stopResponse.accessToken)
        assertEquals(adminId.toString(), decoded.subject)
        assertTrue(decoded.getClaim("original_admin_id").isMissing || decoded.getClaim("original_admin_id").isNull)
        assertTrue(decoded.getClaim("act_email").isMissing || decoded.getClaim("act_email").isNull)
        assertTrue(decoded.getClaim("act_sub").isMissing || decoded.getClaim("act_sub").isNull)

        // activity_logs: two rows for IMPERSONATION_STOP (admin + target perspective).
        val adminStopLogs = dsl.selectFrom(ACTIVITY_LOGS)
            .where(ACTIVITY_LOGS.USER_ID.eq(adminId))
            .and(ACTIVITY_LOGS.ACTION_TYPE.eq("IMPERSONATION_STOP"))
            .fetch()
        val targetStopLogs = dsl.selectFrom(ACTIVITY_LOGS)
            .where(ACTIVITY_LOGS.USER_ID.eq(targetId))
            .and(ACTIVITY_LOGS.ACTION_TYPE.eq("IMPERSONATION_STOP"))
            .fetch()
        assertEquals(1, adminStopLogs.size, "expected 1 admin-perspective STOP log row")
        assertEquals(1, targetStopLogs.size, "expected 1 target-perspective STOP log row")
    }

    @Test
    fun refreshRejectsImpersonationRefreshTokens() {
        val adminResponse = authService.register("admin@example.com", "adminpass123", "Admin")
        val targetResponse = authService.register("target@example.com", "targetpass123", "Target User")
        val adminId = adminResponse.user.id.toInt()
        val targetId = targetResponse.user.id.toInt()

        val impersonationResponse = authService.startImpersonation(adminId, targetId)
        val impersonationRefreshToken = impersonationResponse.refreshToken

        val exception = assertThrows(AuthenticationException::class.java) {
            authService.refresh(impersonationRefreshToken)
        }
        assertTrue(
            exception.message!!.contains("Impersonation sessions do not support refresh rotation"),
            "unexpected message: ${exception.message}"
        )

        // Token must NOT be revoked — refresh refused before any DB mutation.
        val row = dsl.selectFrom(REFRESH_TOKENS)
            .where(REFRESH_TOKENS.TOKEN.eq(impersonationRefreshToken))
            .fetchOne()
        assertNotNull(row)
        assertEquals(0, row!!.revoked, "impersonation refresh token must NOT be revoked by refresh rejection")
    }
}
