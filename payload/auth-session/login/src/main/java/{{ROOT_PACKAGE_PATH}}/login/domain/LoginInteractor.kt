package {{ROOT_PACKAGE}}.login.domain

/** Feature-level outcome of a sign-in attempt (DATA-03). The screen renders each one differently. */
sealed interface SignInOutcome {
    /** Signed in. The host navigates away; the screen does nothing. */
    data object Success : SignInOutcome

    data object InvalidCredentials : SignInOutcome

    data object AccountDisabled : SignInOutcome

    /** Any other definitive refusal — one generic message; the reason is logged, never shown. */
    data object Refused : SignInOutcome

    /** Nothing is known (offline, server trouble). Keep the form; retrying may help. */
    data object Unavailable : SignInOutcome
}

/** The only thing the sign-in ViewModel talks to (DATA-02). */
interface LoginInteractor {

    /** Validation is the caller's (on submit, DATA-31); this only talks to the session. Main-safe. */
    suspend fun signIn(email: String, password: String): SignInOutcome

    /**
     * Local-first sign-out: a best-effort server revocation, then the local wipe — always, whatever the network
     * did. Call it from wherever the app offers "Sign out"; the session host extension then navigates.
     */
    suspend fun signOut()
}
