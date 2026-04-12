package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.UsersRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.USERS
import java.time.Instant

class UserRepository(private val dsl: DSLContext) {

    fun create(
        email: String,
        passwordHash: String,
        displayName: String,
        isSuperAdmin: Boolean,
        forcePasswordChange: Boolean
    ): UsersRecord {
        val now = Instant.now().toString()
        return dsl.insertInto(USERS)
            .set(USERS.EMAIL, email)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.DISPLAY_NAME, displayName)
            .set(USERS.IS_SUPER_ADMIN, if (isSuperAdmin) 1 else 0)
            .set(USERS.FORCE_PASSWORD_CHANGE, if (forcePasswordChange) 1 else 0)
            .set(USERS.CREATED_AT, now)
            .set(USERS.UPDATED_AT, now)
            .returning()
            .fetchOne()!!
    }

    fun findByEmail(email: String): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne()

    fun findById(id: Int): UsersRecord? =
        dsl.selectFrom(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne()

    fun updatePasswordHash(id: Int, newHash: String) {
        dsl.update(USERS)
            .set(USERS.PASSWORD_HASH, newHash)
            .set(USERS.UPDATED_AT, Instant.now().toString())
            .where(USERS.ID.eq(id))
            .execute()
    }

    fun clearForcePasswordChange(id: Int) {
        dsl.update(USERS)
            .set(USERS.FORCE_PASSWORD_CHANGE, 0)
            .set(USERS.UPDATED_AT, Instant.now().toString())
            .where(USERS.ID.eq(id))
            .execute()
    }

    fun countAll(): Long =
        dsl.selectCount()
            .from(USERS)
            .fetchOne(0, Long::class.java) ?: 0L
}
