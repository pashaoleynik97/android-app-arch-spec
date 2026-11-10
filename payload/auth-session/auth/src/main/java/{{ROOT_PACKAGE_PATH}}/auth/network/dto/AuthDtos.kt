package {{ROOT_PACKAGE}}.auth.network.dto

import kotlinx.serialization.Serializable

/*
 * EXAMPLE CONTRACT — the auth endpoints the mitm-testing fake backend serves (wrapped in the network module's
 * ApiResponse / error envelope). Adapt the field names to your backend; keep the shapes small.
 *
 *   POST v1/auth/login    {email, password}  -> {accessToken, refreshToken, expiresIn, user{id, email, displayName}}
 *   POST v1/auth/refresh  {refreshToken}     -> {accessToken, refreshToken, expiresIn}   (rotated; single-use)
 *   POST v1/auth/logout   Bearer access      -> {success}
 */

@Serializable
data class LoginRequestDto(val email: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class AuthUserDto(
    val id: String,
    val email: String,
    val displayName: String = "",
)

@Serializable
data class LoginResultDto(
    val accessToken: String,
    val refreshToken: String,
    /** Access-token lifetime in seconds. The JWT is never parsed: this is the only source of the lifetime. */
    val expiresIn: Long,
    val user: AuthUserDto,
)

/** Refresh returns tokens only — identity does not change across rotation. */
@Serializable
data class RefreshResultDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
)
