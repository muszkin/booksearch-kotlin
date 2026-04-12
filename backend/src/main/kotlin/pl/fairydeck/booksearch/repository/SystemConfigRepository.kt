package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.references.SYSTEM_CONFIG

class SystemConfigRepository(private val dsl: DSLContext) {

    companion object {
        private const val REGISTRATION_ENABLED_KEY = "registration_enabled"
    }

    fun getValue(key: String): String? =
        dsl.select(SYSTEM_CONFIG.VALUE)
            .from(SYSTEM_CONFIG)
            .where(SYSTEM_CONFIG.KEY.eq(key))
            .fetchOne(SYSTEM_CONFIG.VALUE)

    fun setValue(key: String, value: String) {
        dsl.insertInto(SYSTEM_CONFIG)
            .set(SYSTEM_CONFIG.KEY, key)
            .set(SYSTEM_CONFIG.VALUE, value)
            .onConflict(SYSTEM_CONFIG.KEY)
            .doUpdate()
            .set(SYSTEM_CONFIG.VALUE, value)
            .execute()
    }

    fun isRegistrationEnabled(): Boolean =
        getValue(REGISTRATION_ENABLED_KEY) == "true"

    fun setRegistrationEnabled(enabled: Boolean) {
        setValue(REGISTRATION_ENABLED_KEY, enabled.toString())
    }
}
