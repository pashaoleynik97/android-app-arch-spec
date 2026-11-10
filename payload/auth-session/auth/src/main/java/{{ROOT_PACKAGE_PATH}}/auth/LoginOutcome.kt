package {{ROOT_PACKAGE}}.auth

/**
 * Outcome of [AuthManager.login] — a sealed result the sign-in screen branches on exhaustively (DATA-03).
 * Only [InvalidCredentials] and [AccountDisabled] are shown with their own message; every other definitive
 * refusal reads the same to the user and carries its code for the log only.
 */
sealed interface LoginOutcome {

    data object Success : LoginOutcome

    /** The email / password pair was refused. */
    data object InvalidCredentials : LoginOutcome

    /** The account exists but may not sign in. */
    data object AccountDisabled : LoginOutcome

    /** Any other definitive refusal (malformed input the server rejected, …). [code] is for logs, never for UI. */
    data class Rejected(val code: String?) : LoginOutcome

    /** Nothing is known (offline, timeout, 5xx, a malformed success body) — keep the form, offer a retry. */
    data class Unavailable(val cause: Throwable?) : LoginOutcome
}
