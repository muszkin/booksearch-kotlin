package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.PasswordResetTokensRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.PASSWORD_RESET_TOKENS
import java.time.Instant

class PasswordResetTokenRepository(private val dsl: DSLContext) {

    fun create(userId: Int, token: String, expiresAt: String): PasswordResetTokensRecord =
        dsl.insertInto(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USER_ID, userId)
            .set(PASSWORD_RESET_TOKENS.TOKEN, token)
            .set(PASSWORD_RESET_TOKENS.EXPIRES_AT, expiresAt)
            .set(PASSWORD_RESET_TOKENS.CREATED_AT, Instant.now().toString())
            .returning()
            .fetchOne()!!

    fun findValidByToken(token: String): PasswordResetTokensRecord? {
        val now = Instant.now().toString()
        return dsl.selectFrom(PASSWORD_RESET_TOKENS)
            .where(PASSWORD_RESET_TOKENS.TOKEN.eq(token))
            .and(PASSWORD_RESET_TOKENS.USED.eq(0))
            .and(PASSWORD_RESET_TOKENS.EXPIRES_AT.gt(now))
            .fetchOne()
    }

    fun markAsUsed(token: String) {
        dsl.update(PASSWORD_RESET_TOKENS)
            .set(PASSWORD_RESET_TOKENS.USED, 1)
            .where(PASSWORD_RESET_TOKENS.TOKEN.eq(token))
            .execute()
    }
}
