package pl.fairydeck.booksearch.api

import io.ktor.server.auth.*

data class UserPrincipal(
    val userId: Int,
    val email: String,
    val isSuperAdmin: Boolean,
    val originalAdminId: Int? = null
) : Principal
