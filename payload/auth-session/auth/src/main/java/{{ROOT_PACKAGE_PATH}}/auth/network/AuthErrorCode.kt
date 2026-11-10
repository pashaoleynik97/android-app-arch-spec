package {{ROOT_PACKAGE}}.auth.network

/**
 * The error codes the session layer classifies on — **example contract** (the codes the mitm-testing fake
 * backend answers with). Everything else is [UNKNOWN] and handled generically; an unknown code never crashes.
 */
enum class AuthErrorCode {
    /** 401 on login — wrong email / password. */
    INVALID_CREDENTIALS,

    /** 400 on login — the request was malformed (a missing field). A definitive refusal, not a transient error. */
    VALIDATION_ERROR,

    /** 400 / 403 on login — the account may not sign in. */
    ACCOUNT_DISABLED,

    /** 401 on refresh — unknown, expired or already-used refresh token: the session is over. */
    REFRESH_TOKEN_INVALID,

    /** 401 on refresh — the session reached its end (idle window or lifetime). */
    SESSION_EXPIRED,

    /** 401 on refresh — the session was revoked deliberately; `details.reason` may say why. */
    SESSION_REVOKED,

    UNKNOWN,
    ;

    companion object {
        fun fromWire(code: String?): AuthErrorCode = entries.firstOrNull { it.name == code } ?: UNKNOWN
    }
}
