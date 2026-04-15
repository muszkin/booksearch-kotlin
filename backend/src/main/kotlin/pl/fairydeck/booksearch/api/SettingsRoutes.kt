package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import pl.fairydeck.booksearch.repository.UserSettingsRepository
import pl.fairydeck.booksearch.service.ActivityLogService

private val ALLOWED_DEVICES = setOf("kindle", "pocketbook")
private const val REDACTED_PASSWORD = "********"

private val SETTING_KEYS = listOf("smtp_host", "smtp_port", "smtp_username", "smtp_password", "smtp_from", "recipient_email")

fun Route.settingsRoutes(userSettingsRepository: UserSettingsRepository, activityLogService: ActivityLogService) {
    authenticate("jwt") {
        route("/api/settings") {

            get {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val allSettings = userSettingsRepository.getAllForUser(principal.userId)
                val grouped = mutableMapOf<String, MutableMap<String, String>>()

                for (record in allSettings) {
                    val key = record.settingKey ?: continue
                    val device = extractDevice(key) ?: continue
                    val field = extractField(key) ?: continue
                    grouped.getOrPut(device) { mutableMapOf() }[field] = record.settingValue ?: ""
                }

                val result = mutableMapOf<String, DeviceSettingsResponse>()
                for ((device, fields) in grouped) {
                    result[device] = toDeviceSettingsResponse(fields)
                }

                call.respond(HttpStatusCode.OK, result)
            }

            get("/{device}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val device = call.parameters["device"]
                    ?: throw ValidationException("Missing device parameter")

                validateDevice(device)

                val prefix = "${device}_"
                val settings = userSettingsRepository.getByPrefix(principal.userId, prefix)

                if (settings.isEmpty()) {
                    throw NotFoundException("Settings for $device not found")
                }

                val fields = settings.mapKeys { (key, _) ->
                    key.removePrefix(prefix)
                }

                call.respond(HttpStatusCode.OK, toDeviceSettingsResponse(fields))
            }

            put("/{device}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val device = call.parameters["device"]
                    ?: throw ValidationException("Missing device parameter")

                validateDevice(device)

                val request = call.receive<DeviceSettingsRequest>()
                val prefix = "${device}_"

                val settingsMap = mapOf(
                    "${prefix}smtp_host" to request.host,
                    "${prefix}smtp_port" to request.port.toString(),
                    "${prefix}smtp_username" to request.username,
                    "${prefix}smtp_password" to request.password,
                    "${prefix}smtp_from" to request.fromEmail,
                    "${prefix}recipient_email" to request.recipientEmail
                )

                userSettingsRepository.setAll(principal.userId, settingsMap)
                activityLogService.log(principal.userId, "SETTINGS_CHANGED", "settings", device)

                call.respond(HttpStatusCode.OK, mapOf("message" to "Settings saved successfully"))
            }

            delete("/{device}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val device = call.parameters["device"]
                    ?: throw ValidationException("Missing device parameter")

                validateDevice(device)

                userSettingsRepository.deleteByPrefix(principal.userId, "${device}_")

                call.respond(HttpStatusCode.OK, mapOf("message" to "Settings deleted successfully"))
            }
        }
    }
}

private fun validateDevice(device: String) {
    if (device !in ALLOWED_DEVICES) {
        throw ValidationException("Device must be one of: ${ALLOWED_DEVICES.joinToString(", ")}")
    }
}

private fun extractDevice(settingKey: String): String? {
    val device = settingKey.substringBefore("_")
    return if (device in ALLOWED_DEVICES) device else null
}

private fun extractField(settingKey: String): String? {
    val device = settingKey.substringBefore("_")
    if (device !in ALLOWED_DEVICES) return null
    return settingKey.removePrefix("${device}_")
}

private fun toDeviceSettingsResponse(fields: Map<String, String>): DeviceSettingsResponse =
    DeviceSettingsResponse(
        host = fields["smtp_host"] ?: "",
        port = fields["smtp_port"] ?: "",
        username = fields["smtp_username"] ?: "",
        password = if (fields.containsKey("smtp_password")) REDACTED_PASSWORD else "",
        fromEmail = fields["smtp_from"] ?: "",
        recipientEmail = fields["recipient_email"] ?: ""
    )

@Serializable
data class DeviceSettingsRequest(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val fromEmail: String,
    val recipientEmail: String
)

@Serializable
data class DeviceSettingsResponse(
    val host: String,
    val port: String,
    val username: String,
    val password: String,
    val fromEmail: String,
    val recipientEmail: String
)
