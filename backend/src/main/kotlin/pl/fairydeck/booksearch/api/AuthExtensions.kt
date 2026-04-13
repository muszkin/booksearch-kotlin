package pl.fairydeck.booksearch.api

import io.ktor.server.application.*
import io.ktor.server.auth.*

fun requireSuperAdmin(call: ApplicationCall) {
    val principal = call.principal<UserPrincipal>()
        ?: throw AuthenticationException("Authentication required")
    if (!principal.isSuperAdmin) {
        throw AuthorizationException("Super-admin access required")
    }
}
