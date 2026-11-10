package {{ROOT_PACKAGE}}.auth

/**
 * The signed-in user, as the login response describes them (example contract: `id`, `email`, `displayName`).
 * Refresh returns no user, so this is a login-time snapshot until the next login. Plain domain data; the wire
 * DTO maps onto it.
 */
data class SessionUser(
    val id: String,
    val email: String,
    val displayName: String,
)
