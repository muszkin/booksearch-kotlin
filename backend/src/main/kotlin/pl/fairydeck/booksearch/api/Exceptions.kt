package pl.fairydeck.booksearch.api

class AuthenticationException(message: String) : RuntimeException(message)

class AuthorizationException(message: String) : RuntimeException(message)

class ConflictException(message: String) : RuntimeException(message)

class ValidationException(message: String) : RuntimeException(message)

class NotFoundException(message: String) : RuntimeException(message)
