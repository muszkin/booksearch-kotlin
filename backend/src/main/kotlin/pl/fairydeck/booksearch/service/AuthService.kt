package pl.fairydeck.booksearch.service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.slf4j.LoggerFactory
import pl.fairydeck.booksearch.api.AuthenticationException
import pl.fairydeck.booksearch.api.AuthorizationException
import pl.fairydeck.booksearch.api.ConflictException
import pl.fairydeck.booksearch.api.NotFoundException
import pl.fairydeck.booksearch.api.ValidationException
import pl.fairydeck.booksearch.jooq.generated.tables.records.UsersRecord
import pl.fairydeck.booksearch.models.LoginResponse
import pl.fairydeck.booksearch.models.RefreshResponse
import pl.fairydeck.booksearch.models.UserResponse
import pl.fairydeck.booksearch.repository.PasswordResetTokenRepository
import pl.fairydeck.booksearch.repository.RefreshTokenRepository
import pl.fairydeck.booksearch.repository.SystemConfigRepository
import pl.fairydeck.booksearch.repository.UserRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID

class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val systemConfigRepository: SystemConfigRepository,
    private val jwtSecret: String,
    private val jwtIssuer: String,
    private val jwtAudience: String,
    private val accessTokenExpirationMs: Long,
    private val refreshTokenExpirationMs: Long
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val algorithm = Algorithm.HMAC256(jwtSecret)

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

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

    fun refresh(refreshToken: String): RefreshResponse {
        val tokenRecord = refreshTokenRepository.findValidByToken(refreshToken)
            ?: throw AuthenticationException("Invalid or expired refresh token")

        val user = userRepository.findById(tokenRecord.userId!!)
            ?: throw AuthenticationException("User not found")

        if (user.isActive != 1) {
            throw AuthenticationException("Account is deactivated")
        }

        val accessToken = generateAccessToken(user)
        return RefreshResponse(accessToken = accessToken)
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

    fun changeUserPassword(targetUserId: Int, newPassword: String) {
        validatePassword(newPassword)

        val user = userRepository.findById(targetUserId)
            ?: throw NotFoundException("User not found")

        val newHash = hashPassword(newPassword)
        userRepository.updatePasswordHash(user.id!!, newHash)
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

    private fun generateAccessToken(user: UsersRecord): String {
        val now = Date()
        val expiration = Date(now.time + accessTokenExpirationMs)

        return JWT.create()
            .withIssuer(jwtIssuer)
            .withAudience(jwtAudience)
            .withSubject(user.id.toString())
            .withClaim("email", user.email)
            .withClaim("is_super_admin", user.isSuperAdmin == 1)
            .withIssuedAt(now)
            .withExpiresAt(expiration)
            .sign(algorithm)
    }

    private fun generateRefreshToken(userId: Int): String {
        val token = UUID.randomUUID().toString()
        val expiresAt = Instant.now().plusMillis(refreshTokenExpirationMs).toString()
        refreshTokenRepository.create(userId, token, expiresAt)
        return token
    }

    private fun toUserResponse(user: UsersRecord): UserResponse =
        UserResponse(
            id = user.id!!.toLong(),
            email = user.email!!,
            displayName = user.displayName!!,
            isSuperAdmin = user.isSuperAdmin == 1,
            isActive = user.isActive == 1,
            forcePasswordChange = user.forcePasswordChange == 1,
            createdAt = user.createdAt!!
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
