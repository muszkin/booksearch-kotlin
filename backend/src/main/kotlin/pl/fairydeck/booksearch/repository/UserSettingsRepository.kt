package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.records.UserSettingsRecord
import pl.fairydeck.booksearch.jooq.generated.tables.references.USER_SETTINGS
import java.time.Instant

class UserSettingsRepository(private val dsl: DSLContext) {

    fun set(userId: Int, key: String, value: String) {
        val now = Instant.now().toString()

        val existing = dsl.selectFrom(USER_SETTINGS)
            .where(USER_SETTINGS.USER_ID.eq(userId))
            .and(USER_SETTINGS.SETTING_KEY.eq(key))
            .fetchOne()

        if (existing != null) {
            dsl.update(USER_SETTINGS)
                .set(USER_SETTINGS.SETTING_VALUE, value)
                .set(USER_SETTINGS.UPDATED_AT, now)
                .where(USER_SETTINGS.ID.eq(existing.id))
                .execute()
        } else {
            dsl.insertInto(USER_SETTINGS)
                .set(USER_SETTINGS.USER_ID, userId)
                .set(USER_SETTINGS.SETTING_KEY, key)
                .set(USER_SETTINGS.SETTING_VALUE, value)
                .set(USER_SETTINGS.CREATED_AT, now)
                .set(USER_SETTINGS.UPDATED_AT, now)
                .execute()
        }
    }

    fun get(userId: Int, key: String): String? =
        dsl.selectFrom(USER_SETTINGS)
            .where(USER_SETTINGS.USER_ID.eq(userId))
            .and(USER_SETTINGS.SETTING_KEY.eq(key))
            .fetchOne()
            ?.settingValue

    fun getAllForUser(userId: Int): List<UserSettingsRecord> =
        dsl.selectFrom(USER_SETTINGS)
            .where(USER_SETTINGS.USER_ID.eq(userId))
            .fetch()

    fun deleteByPrefix(userId: Int, prefix: String) {
        dsl.deleteFrom(USER_SETTINGS)
            .where(USER_SETTINGS.USER_ID.eq(userId))
            .and(USER_SETTINGS.SETTING_KEY.startsWith(prefix))
            .execute()
    }

    fun setAll(userId: Int, settings: Map<String, String>) {
        settings.forEach { (key, value) -> set(userId, key, value) }
    }

    fun getByPrefix(userId: Int, prefix: String): Map<String, String> =
        dsl.selectFrom(USER_SETTINGS)
            .where(USER_SETTINGS.USER_ID.eq(userId))
            .and(USER_SETTINGS.SETTING_KEY.startsWith(prefix))
            .fetch()
            .associate { it.settingKey!! to (it.settingValue ?: "") }
}
