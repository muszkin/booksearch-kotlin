package pl.fairydeck.booksearch.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.AuthenticationException
import pl.fairydeck.booksearch.api.AuthorizationException
import pl.fairydeck.booksearch.api.ConflictException
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.api.UserPrincipal
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.jooq.generated.tables.records.UsersRecord
import pl.fairydeck.booksearch.models.LoginResponse
import pl.fairydeck.booksearch.models.RefreshResponse
import pl.fairydeck.booksearch.models.UserResponse
import pl.fairydeck.booksearch.repository.PasswordResetTokenRepository
import pl.fairydeck.booksearch.repository.RefreshTokenRepository
import pl.fairydeck.booksearch.repository.SystemConfigRepository
import pl.fairydeck.booksearch.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

class AuthService(
    private val dsl: DSLContext,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val systemConfigRepository: SystemConfigRepository,
    private val activityLogService: ActivityLogService,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
    private val accessTokenExpirationMs: Long,
    private val refreshTokenExpirationMs: Long
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    companion object {
        private const val IMPERSONATION_ACCESS_TTL_MS: Long = 30L * 60L * 1000L
        private const val IMPERSONATION_REFRESH_TTL_MS: Long = 60L * 60L * 1000L
        private const val IMPERSONATION_CAP_DETECTION_MS: Long = 24L * 60L * 60L * 1000L
    }

    private data class ImpersonationContext(
        val adminUserId: Int,
        val adminEmail: String
    )

    fun register(email: String, password: String, displayName: String): LoginResponse {
        validateRegistrationInput(email, password, displayName)

        val isFirstUser = userRepository.countAll() == 0L
        if (!isFirstUser && !systemConfigRepository.isRegistrationEnabled()) {
            throw AuthenticationException("Registration is currently disabled")
        }

        if (userRepository.findByEmail(email) != null) {
            throw ConflictException("User with email '$email' already exists")
        }

        val passwordHash = hashPassword(password)
        val user = userRepository.create(
            email = email,
            passwordHash = passwordHash,
            displayName = displayName,
            isSuperAdmin = isFirstUser,
            forcePasswordChange = false
        )

        return buildLoginResponse(user)
    }

    fun login(email: String, password: String): LoginResponse {
        val user = userRepository.findByEmail(email)
            ?: throw AuthenticationException("Invalid email or password")

        if (user.isActive != 1) {
            throw AuthenticationException("Account is deactivated")
        }

        if (!verifyPassword(password, user.passwordHash!!)) {
            throw AuthenticationException("Invalid email or password")
        }

        return buildLoginResponse(user)
    }

    fun refresh(refreshToken: String): RefreshResponse = dsl.transactionResult { conf ->
        val txRepo = RefreshTokenRepository(conf.dsl())
        val tokenRecord = txRepo.findValidByToken(refreshToken)
            ?: throw AuthenticationException("Invalid or expired refresh token")

        val user = userRepository.findById(tokenRecord.userId!!)
            ?: throw AuthenticationException("User not found")

        if (user.isActive != 1) {
            throw AuthenticationException("Account is deactivated")
        }

        // Impersonation refresh tokens carry a short absolute TTL (<= IMPERSONATION_CAP_DETECTION_MS).
        // We refuse to rotate them — the 30min access TTL is comfortable under the 1h refresh cap,
        // so clients do not need /auth/refresh during an impersonation session. Throw BEFORE any
        // DB mutation so the token stays usable until /admin/impersonate/stop revokes it explicitly.
        val createdAt = Instant.parse(tokenRecord.createdAt)
        val expiresAt = Instant.parse(tokenRecord.expiresAt)
        val windowMs = Duration.between(createdAt, expiresAt).toMillis()
        if (windowMs < IMPERSONATION_CAP_DETECTION_MS) {
            logger.warn(
                "Impersonation refresh rotation denied: adminUserId={} tokenCreatedAt={}",
                tokenRecord.userId, tokenRecord.createdAt
            )
            throw AuthenticationException("Impersonation sessions do not support refresh rotation")
        }

        txRepo.revokeByToken(refreshToken)
        val newRefreshToken = UUID.randomUUID().toString()
        val newExpiresAt = Instant.now().plusMillis(refreshTokenExpirationMs).toString()
        txRepo.create(user.id!!, newRefreshToken, newExpiresAt)

        val accessToken = generateAccessToken(user)
        return@transactionResult RefreshResponse(
            accessToken = accessToken,
            refreshToken = newRefreshToken,
            user = toUserResponse(user)
        )
    }

    fun getCurrentUser(principal: UserPrincipal): UserResponse {
        val user = userRepository.findById(principal.userId)
            ?: throw NotFoundException("User not found")
        if (user.isActive != 1) {
            throw AuthenticationException("Account is deactivated")
        }
        if (principal.originalAdminId != null) {
            val admin = userRepository.findById(principal.originalAdminId)
                ?: throw NotFoundException("Original admin not found")
            return toUserResponse(user, actAsUserId = admin.id!!, actAsEmail = admin.email!!)
        }
        return toUserResponse(user)
    }

    fun requestPasswordReset(email: String) {
        val user = userRepository.findByEmail(email) ?: return

        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS).toString()
        passwordResetTokenRepository.create(user.id!!, token, expiresAt)

        logger.info("Password reset token generated for user '{}'", email)
        logger.debug("Password reset token for user '{}': {}", email, token)
    }

    fun resetPassword(token: String, newPassword: String) {
        validatePassword(newPassword)

        val tokenRecord = passwordResetTokenRepository.findValidByToken(token)
            ?: throw AuthenticationException("Invalid or expired password reset token")

        val user = userRepository.findById(tokenRecord.userId!!)
            ?: throw NotFoundException("User not found")

        val newHash = hashPassword(newPassword)
        userRepository.updatePasswordHash(user.id!!, newHash)
        userRepository.clearForcePasswordChange(user.id!!)
        passwordResetTokenRepository.markAsUsed(token)
        refreshTokenRepository.revokeAllByUserId(user.id!!)
    }

    fun changePassword(userId: Int, currentPassword: String, newPassword: String) {
        validatePassword(newPassword)

        val user = userRepository.findById(userId)
            ?: throw NotFoundException("User not found")

        if (!verifyPassword(currentPassword, user.passwordHash!!)) {
            throw AuthenticationException("Current password is incorrect")
        }

        val newHash = hashPassword(newPassword)
        userRepository.updatePasswordHash(userId, newHash)
        userRepository.clearForcePasswordChange(userId)
    }

    fun logout(refreshToken: String) {
        refreshTokenRepository.revokeByToken(refreshToken)
    }

    fun toggleRegistration(enabled: Boolean) {
        systemConfigRepository.setRegistrationEnabled(enabled)
    }

    fun createUser(email: String, displayName: String, temporaryPassword: String): UserResponse {
        validateRegistrationInput(email, temporaryPassword, displayName)

        if (userRepository.findByEmail(email) != null) {
            throw ConflictException("User with email '$email' already exists")
        }

        val passwordHash = hashPassword(temporaryPassword)
        val user = userRepository.create(
            email = email,
            passwordHash = passwordHash,
            displayName = displayName,
            isSuperAdmin = false,
            forcePasswordChange = true
        )

        return toUserResponse(user)
    }

    fun listUsers(): List<UserResponse> =
        userRepository.findAll().map { toUserResponse(it) }

    fun changeUserPassword(targetUserId: Int, newPassword: String) {
        validatePassword(newPassword)

        val user = userRepository.findById(targetUserId)
            ?: throw NotFoundException("User not found")

        val newHash = hashPassword(newPassword)
        userRepository.updatePasswordHash(user.id!!, newHash)
    }

    fun startImpersonation(adminUserId: Int, targetUserId: Int): LoginResponse =
        dsl.transactionResult { conf ->
            val txRefreshRepo = RefreshTokenRepository(conf.dsl())

            if (targetUserId == adminUserId) {
                throw ValidationException("Cannot impersonate yourself")
            }
            val admin = userRepository.findById(adminUserId)
                ?: throw NotFoundException("Admin user not found")
            val target = userRepository.findById(targetUserId)
                ?: throw NotFoundException("Target user not found")
            if (target.isSuperAdmin == 1) {
                throw AuthorizationException("Cannot impersonate another super admin")
            }
            if (target.isActive != 1) {
                throw ValidationException("Cannot impersonate an inactive user")
            }

            val impersonationCtx = ImpersonationContext(
                adminUserId = admin.id!!,
                adminEmail = admin.email!!
            )
            val accessToken = generateAccessToken(target, impersonationCtx)

            val refreshToken = UUID.randomUUID().toString()
            val refreshExpiresAt = Instant.now().plusMillis(IMPERSONATION_REFRESH_TTL_MS).toString()
            // user_id = adminUserId: ownership attaches the refresh to the real admin so stop()
            // can revoke it cleanly and prove the session belongs to this admin (K2 mitigation).
            txRefreshRepo.create(admin.id!!, refreshToken, refreshExpiresAt)

            activityLogService.logDual(
                adminUserId = admin.id!!,
                targetUserId = target.id!!,
                actionType = "IMPERSONATION_START",
                entityType = "USER",
                entityId = target.id!!.toString(),
                adminDetails = kotlinx.serialization.json.buildJsonObject {
                    put("target_user_id", kotlinx.serialization.json.JsonPrimitive(target.id))
                    put("target_email", kotlinx.serialization.json.JsonPrimitive(target.email))
                }.toString(),
                targetDetails = kotlinx.serialization.json.buildJsonObject {
                    put("impersonated_by_admin_id", kotlinx.serialization.json.JsonPrimitive(admin.id))
                    put("admin_email", kotlinx.serialization.json.JsonPrimitive(admin.email))
                }.toString()
            )

            LoginResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = toUserResponse(target, actAsUserId = admin.id!!, actAsEmail = admin.email!!)
            )
        }

    fun stopImpersonation(
        currentRefreshToken: String,
        originalAdminId: Int,
        impersonatedUserId: Int
    ): LoginResponse = dsl.transactionResult { conf ->
        val txRefreshRepo = RefreshTokenRepository(conf.dsl())

        val tokenRecord = txRefreshRepo.findValidByToken(currentRefreshToken)
            ?: throw AuthenticationException("Invalid or expired refresh token")
        if (tokenRecord.userId != originalAdminId) {
            throw AuthorizationException("Refresh token does not belong to the original admin")
        }

        val admin = userRepository.findById(originalAdminId)
            ?: throw NotFoundException("Admin user not found")
        if (admin.isActive != 1) {
            throw AuthenticationException("Admin account is deactivated")
        }

        txRefreshRepo.revokeByToken(currentRefreshToken)

        val newAccessToken = generateAccessToken(admin)
        val newRefreshToken = UUID.randomUUID().toString()
        val newExpiresAt = Instant.now().plusMillis(refreshTokenExpirationMs).toString()
        txRefreshRepo.create(admin.id!!, newRefreshToken, newExpiresAt)

        activityLogService.logDual(
            adminUserId = admin.id!!,
            targetUserId = impersonatedUserId,
            actionType = "IMPERSONATION_STOP",
            entityType = "USER",
            entityId = impersonatedUserId.toString(),
            adminDetails = kotlinx.serialization.json.buildJsonObject {
                put("target_user_id", kotlinx.serialization.json.JsonPrimitive(impersonatedUserId))
            }.toString(),
            targetDetails = kotlinx.serialization.json.buildJsonObject {
                put("impersonated_by_admin_id", kotlinx.serialization.json.JsonPrimitive(admin.id))
            }.toString()
        )

        return@transactionResult LoginResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            user = toUserResponse(admin)
        )
    }

    private fun buildLoginResponse(user: UsersRecord): LoginResponse {
        val accessToken = generateAccessToken(user)
        val refreshToken = generateRefreshToken(user.id!!)
        return LoginResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = toUserResponse(user)
        )
    }

    private fun generateAccessToken(user: UsersRecord): String =
        generateAccessToken(user, impersonation = null)

    private fun generateAccessToken(user: UsersRecord, impersonation: ImpersonationContext?): String {
        val now = Date()
        val ttlMs = if (impersonation != null) IMPERSONATION_ACCESS_TTL_MS else accessTokenExpirationMs
        val expiration = Date(now.time + ttlMs)

        val builder = JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withSubject(user.id.toString())
            .withClaim("email", user.email)
            .withClaim("is_super_admin", user.isSuperAdmin == 1)
            .withIssuedAt(now)
            .withExpiresAt(expiration)

        if (impersonation != null) {
            // Audit IM2: emit only original_admin_id + act_email (no redundant act_sub).
            builder
                .withClaim("original_admin_id", impersonation.adminUserId)
                .withClaim("act_email", impersonation.adminEmail)
        }

        return builder.sign(algorithm)
    }

    private fun generateRefreshToken(userId: Int): String {
        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusMillis(refreshTokenExpirationMs).toString()
        refreshTokenRepository.create(userId, token, expiresAt)
        return token
    }

    private fun toUserResponse(
        user: UsersRecord,
        actAsUserId: Int? = null,
        actAsEmail: String? = null
    ): UserResponse =
        UserResponse(
            id = user.id!!.toLong(),
            email = user.email!!,
            displayName = user.displayName!!,
            isSuperAdmin = user.isSuperAdmin == 1,
            isActive = user.isActive == 1,
            forcePasswordChange = user.forcePasswordChange == 1,
            createdAt = user.createdAt!!,
            actAsUserId = actAsUserId?.toLong(),
            actAsEmail = actAsEmail
        )

    private fun validateRegistrationInput(email: String, password: String, displayName: String) {
        validateEmail(email)
        validatePassword(password)
        validateDisplayName(displayName)
    }

    private fun validateEmail(email: String) {
        if (!emailRegex.matches(email)) {
            throw ValidationException("Invalid email format")
        }
    }

    private fun validatePassword(password: String) {
        if (password.length < 8) {
            throw ValidationException("Password must be at least 8 characters")
        }
        if (password.length > 128) {
            throw ValidationException("Password must be at most 128 characters")
        }
    }

    private fun validateDisplayName(displayName: String) {
        if (displayName.isBlank()) {
            throw ValidationException("Display name must not be blank")
        }
        if (displayName.length > 100) {
            throw ValidationException("Display name must be at most 100 characters")
        }
    }

    private fun hashPassword(password: String): String =
        BCrypt.withDefaults().hashToString(12, password.toCharArray())

    private fun verifyPassword(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}
