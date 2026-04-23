package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pl.fairydeck.booksearch.service.ActivityLogService
import pl.fairydeck.booksearch.service.DeliveryService

fun Route.deliverRoutes(deliveryService: DeliveryService, activityLogService: ActivityLogService) {
    authenticate("jwt") {
        route("/api/deliver") {
            post("/{libraryId}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val libraryId = call.parameters["libraryId"]?.toIntOrNull()
                    ?: throw ValidationException("Invalid library entry ID")

                val device = call.request.queryParameters["device"]
                    ?: throw ValidationException("Missing 'device' query parameter")

                val response = deliveryService.deliver(principal.userId, libraryId, device)
                val action = if (response.status == "sent") "BOOK_DELIVERED" else "BOOK_DELIVERY_FAILED"
                val details = buildString {
                    append("device=$device")
                    response.error?.let { append(" error=$it") }
                }
                activityLogService.log(principal.userId, action, "library_entry", libraryId.toString(), details)
                call.respond(HttpStatusCode.OK, response)
            }
        }

        route("/api/deliveries") {
            get {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val deliveries = deliveryService.getDeliveriesForUser(principal.userId)
                call.respond(HttpStatusCode.OK, deliveries)
            }

            get("/{bookMd5}") {
                val principal = call.principal<UserPrincipal>()
                    ?: throw AuthenticationException("Authentication required")

                val bookMd5 = call.parameters["bookMd5"]
                    ?: throw ValidationException("Missing book MD5 parameter")

                val deliveries = deliveryService.getDeliveriesForBook(principal.userId, bookMd5)
                call.respond(HttpStatusCode.OK, deliveries)
            }
        }
    }
}
