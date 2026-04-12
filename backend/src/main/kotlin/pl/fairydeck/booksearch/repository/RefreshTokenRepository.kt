package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.RefreshTokensRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.REFRESH_TOKENS
import java.time.Instant

class RefreshTokenRepository(private val dsl: DSLContext) {

    fun create(userId: Int, token: String, expiresAt: String): RefreshTokensRecord =
        dsl.insertInto(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.USER_ID, userId)
            .set(REFRESH_TOKENS.TOKEN, token)
            .set(REFRESH_TOKENS.EXPIRES_AT, expiresAt)
            .set(REFRESH_TOKENS.CREATED_AT, Instant.now().toString())
            .returning()
            .fetchOne()!!

    fun findValidByToken(token: String): RefreshTokensRecord? {
        val now = Instant.now().toString()
        return dsl.selectFrom(REFRESH_TOKENS)
            .where(REFRESH_TOKENS.TOKEN.eq(token))
            .and(REFRESH_TOKENS.REVOKED.eq(0))
            .and(REFRESH_TOKENS.EXPIRES_AT.gt(now))
            .fetchOne()
    }

    fun revokeByToken(token: String) {
        dsl.update(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.REVOKED, 1)
            .where(REFRESH_TOKENS.TOKEN.eq(token))
            .execute()
    }

    fun revokeAllByUserId(userId: Int) {
        dsl.update(REFRESH_TOKENS)
            .set(REFRESH_TOKENS.REVOKED, 1)
            .where(REFRESH_TOKENS.USER_ID.eq(userId))
            .execute()
    }
}
