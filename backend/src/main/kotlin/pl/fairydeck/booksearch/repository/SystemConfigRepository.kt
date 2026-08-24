package pl.fairydeck.booksearch.repository

import org.jooq.DSLContext
import pl.fairydeck.booksearch.jooq.generated.tables.references.SYSTEM_CONFIG

class SystemConfigRepository(private val dsl: DSLContext) {

    companion object {
        private const val REGISTRATION_ENABLED_KEY = "registration_enabled"
        private const val DESCRIPTION_STYLE_KEY = "description_style"
        private const val DESCRIPTION_MIN_LENGTH_KEY = "description_min_length"

        /**
         * The half of the description prompt an administrator may rewrite: how a book should
         * be described. The rule forbidding invention is appended by the client and is not
         * editable here — an edit must not be able to disarm it.
         */
        val DEFAULT_DESCRIPTION_STYLE = listOf(
            "You describe books for a library catalogue.",
            "Given a title and author, reply with two to four sentences describing what the",
            "book is about, in the language the title is written in."
        ).joinToString("\n")

        const val DEFAULT_MIN_DESCRIPTION_LENGTH = 80
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

    fun getDescriptionStyle(): String =
        getValue(DESCRIPTION_STYLE_KEY)?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_DESCRIPTION_STYLE

    fun isDescriptionStyleDefault(): Boolean =
        getValue(DESCRIPTION_STYLE_KEY)?.trim().isNullOrEmpty()

    fun setDescriptionStyle(style: String) {
        setValue(DESCRIPTION_STYLE_KEY, style)
    }

    /** Storing a blank value is how a reset is recorded; reads then fall back to the default. */
    fun resetDescriptionStyle() {
        setValue(DESCRIPTION_STYLE_KEY, "")
    }

    fun getMinDescriptionLength(): Int =
        getValue(DESCRIPTION_MIN_LENGTH_KEY)?.toIntOrNull()?.coerceAtLeast(0)
            ?: DEFAULT_MIN_DESCRIPTION_LENGTH

    fun setMinDescriptionLength(length: Int) {
        setValue(DESCRIPTION_MIN_LENGTH_KEY, length.toString())
    }

    fun isRegistrationEnabled(): Boolean =
        getValue(REGISTRATION_ENABLED_KEY) == "true"

    fun setRegistrationEnabled(enabled: Boolean) {
        setValue(REGISTRATION_ENABLED_KEY, enabled.toString())
    }
}
