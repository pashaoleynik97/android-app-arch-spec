package {{ROOT_PACKAGE}}.auth.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.LoginOutcome
import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.auth.SessionResolution
import {{ROOT_PACKAGE}}.auth.SessionUser
import {{ROOT_PACKAGE}}.auth.UserActivityTracker
import {{ROOT_PACKAGE}}.auth.config.ProactiveRefreshConfig
import {{ROOT_PACKAGE}}.auth.di.AuthScope
import {{ROOT_PACKAGE}}.auth.network.AuthErrorCode
import {{ROOT_PACKAGE}}.auth.network.AuthNetworkResult
import {{ROOT_PACKAGE}}.auth.network.AuthService
import {{ROOT_PACKAGE}}.auth.token.ProfileStore
import {{ROOT_PACKAGE}}.auth.token.SecureTokenStore
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import {{ROOT_PACKAGE}}.libcommon.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The session state machine and the **one** refresh entrypoint.
 *
 * ### Concurrency
 * One [Mutex] serialises every identity change — login, logout, refresh commit, bootstrap, end of session — so
 * two flows can never mutate the session at once. Reads are lock-free (off [authState] and `@Volatile` fields),
 * so the bearer interceptor never waits for a refresh.
 *
 * ### Single-flight refresh
 * Concurrent callers share one in-flight [Deferred] ([inFlight]), started on the **app scope** so that a
 * cancelled caller does not cancel the refresh the others await. N concurrent 401s cost one network refresh —
 * required for correctness, because a single-use refresh token presented twice is refused.
 *
 * ### Zombie guard
 * Every identity change bumps [sessionGeneration]. A refresh captures it when it starts and re-checks it
 * **before** going to the network (never spend a single-use token the newer session may hold) and **again
 * before writing** its result; a mismatch writes nothing and answers [RefreshOutcome.Stale]. A dead or replaced
 * session can therefore never be resurrected by a late answer.
 *
 * ### Identity
 * The user id comes from the login response; refresh returns tokens only, so the id is reused from memory or the
 * store. The JWT is never parsed: the lifetime is the response's `expiresIn`.
 */
@Singleton
internal class DefaultAuthManager @Inject constructor(
    private val authService: AuthService,
    private val store: SecureTokenStore,
    private val profileStore: ProfileStore,
    private val dispatchers: DispatcherProvider,
    private val clock: TimeSource,
    private val activityTracker: UserActivityTracker,
    private val proactiveConfig: ProactiveRefreshConfig,
    @param:AuthScope private val scope: CoroutineScope,
) : AuthManager, RefreshCoordinator {

    private val logTag = logTag()

    private val mAuthState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = mAuthState.asStateFlow()

    /** In-memory refresh token (never in [authState]). Written under [mutex]. */
    @Volatile
    private var refreshTokenInMemory: String? = null

    @Volatile
    private var currentUserInMemory: SessionUser? = null

    private val mutex = Mutex()

    /** The shared refresh in flight, or `null`. Touched only under [mutex]. */
    private var inFlight: Deferred<RefreshOutcome>? = null

    /** Bumped on every identity change. Read and written under [mutex]. */
    private var sessionGeneration: Long = 0

    /** Monotonic instant the access token lapses; `0` without a live session. Read by the keep-alive. */
    @Volatile
    private var accessExpiresAt: Long = 0L

    /** Monotonic instant of the last token commit — the activity gate compares interactions against it. */
    @Volatile
    private var lastCommitAt: Long = 0L

    /** The keep-alive loop. Started, replaced and cancelled only under [mutex]. */
    private var proactiveJob: Job? = null

    init {
        // Cold start: resolve any stored session at once, so the state leaves Initializing without a screen asking.
        scope.launch { resolveSession() }
    }

    // region reads (lock-free)

    override fun currentAccessToken(): String? = (mAuthState.value as? AuthState.Authenticated)?.accessToken

    override fun currentRefreshToken(): String? = refreshTokenInMemory

    override fun currentUserId(): String? = (mAuthState.value as? AuthState.Authenticated)?.userId

    override fun currentUser(): SessionUser? = currentUserInMemory

    // endregion

    // region login / logout

    override suspend fun login(email: String, password: String): LoginOutcome {
        // The network call runs outside the lock: a slow login must not block refreshes of a session that is
        // still live. The commit re-checks nothing about the old session — a login replaces it, whatever it was.
        val result = authService.login(email, password)
        return mutex.withLock {
            when (result) {
                is AuthNetworkResult.Success -> {
                    val user = result.user
                    if (user == null) {
                        Reporter.warn("login 2xx without a user: treated as transient", where = logTag)
                        LoginOutcome.Unavailable(null)
                    } else {
                        commitLoginLocked(result, user)
                        LoginOutcome.Success
                    }
                }
                is AuthNetworkResult.AuthRejected -> when (result.code) {
                    AuthErrorCode.INVALID_CREDENTIALS -> LoginOutcome.InvalidCredentials
                    AuthErrorCode.ACCOUNT_DISABLED -> LoginOutcome.AccountDisabled
                    else -> LoginOutcome.Rejected(result.code.name).also {
                        Reporter.warn("login rejected: ${result.code}", where = logTag)
                    }
                }
                is AuthNetworkResult.Transient -> LoginOutcome.Unavailable(result.cause)
            }
        }
    }

    /** A new session replaces whatever was there: new generation (stales any refresh in flight), old tokens wiped. */
    private suspend fun commitLoginLocked(result: AuthNetworkResult.Success, user: SessionUser) {
        sessionGeneration++
        refreshTokenInMemory = result.refreshToken
        currentUserInMemory = user
        store.clear()
        store.save(user.id, result.refreshToken)
        profileStore.save(user)
        recordTokenLifetimeLocked(result.expiresIn)
        mAuthState.value = AuthState.Authenticated(user.id, result.accessToken)
        restartKeepAliveLocked()
    }

    override suspend fun logout() = mutex.withLock {
        sessionGeneration++
        wipeLocked()
        // StateFlow dedupes equal values: a second logout emits nothing.
        mAuthState.value = AuthState.Unauthenticated(AuthState.Reason.LOGGED_OUT)
    }

    override suspend fun revokeRemoteSession() {
        // No lock: this only reads the token and calls the network; the local wipe is logout()'s job.
        val token = currentAccessToken() ?: return
        repeat(MAX_REVOKE_ATTEMPTS) {
            if (authService.logout(token)) return
        }
        Reporter.debug("remote revoke gave up after $MAX_REVOKE_ATTEMPTS attempts", where = logTag)
    }

    override suspend fun endSession(reason: SessionEndReason?) = mutex.withLock {
        if (mAuthState.value is AuthState.Authenticated) {
            sessionGeneration++
            wipeLocked()
            // The coarse reason is only the fallback for the notice; a present precise reason wins.
            mAuthState.value = AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED, reason)
        }
    }

    // endregion

    // region refresh (single-flight)

    override suspend fun refresh(seenAccessToken: String?): RefreshOutcome {
        val deferred: Deferred<RefreshOutcome> = mutex.withLock {
            val current = currentAccessToken()
            // A peer already rotated past the token the caller saw: hand out the current one, no network.
            if (seenAccessToken != null && current != null && current != seenAccessToken) {
                return RefreshOutcome.Refreshed(current)
            }
            val generationAtStart = sessionGeneration
            inFlight ?: scope.async(dispatchers.default) { doRefresh(generationAtStart) }.also { inFlight = it }
        }
        return try {
            deferred.await()
        } finally {
            // NonCancellable: the awaiting caller may be cancelled *by the refresh it awaits* — a refused refresh
            // wipes the session, which cancels the keep-alive loop, which is the usual caller. A cancelled
            // coroutine cannot take a contended lock, so without this the completed `AuthFailed` deferred would
            // stay parked in [inFlight] and every later refresh — even of a brand-new session — would get it back
            // without reaching the network.
            withContext(NonCancellable) {
                mutex.withLock { if (inFlight === deferred) inFlight = null }
            }
        }
    }

    private suspend fun doRefresh(generationAtStart: Long): RefreshOutcome {
        // Check the generation BEFORE the network too: a refresh queued on the app scope may only start after a
        // login landed (cold start racing the user's own sign-in), and it must not spend a single-use token.
        val token = mutex.withLock {
            if (generationAtStart != sessionGeneration) return RefreshOutcome.Stale
            refreshTokenInMemory
        }
        if (token == null) {
            return mutex.withLock {
                if (generationAtStart != sessionGeneration) return@withLock RefreshOutcome.Stale
                sessionGeneration++
                wipeLocked()
                mAuthState.value = AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED)
                RefreshOutcome.AuthFailed
            }
        }

        val result = authService.refresh(token)

        return mutex.withLock {
            if (generationAtStart != sessionGeneration) return@withLock RefreshOutcome.Stale
            when (result) {
                is AuthNetworkResult.Success -> {
                    val userId = currentUserId() ?: store.currentUserId()
                    if (userId == null) {
                        Reporter.warn("refresh succeeded but no user id is known: transient", where = logTag)
                        return@withLock RefreshOutcome.Transient(null)
                    }
                    refreshTokenInMemory = result.refreshToken
                    store.save(userId, result.refreshToken)
                    recordTokenLifetimeLocked(result.expiresIn)
                    mAuthState.value = AuthState.Authenticated(userId, result.accessToken)
                    // A cold-start or reactive refresh gains a keep-alive; the loop's own refresh must not restart
                    // (and thereby cancel) itself.
                    ensureKeepAliveLocked()
                    RefreshOutcome.Refreshed(result.accessToken)
                }
                is AuthNetworkResult.AuthRejected -> {
                    // A refused refresh is an identity change like any other: bump, so nothing in flight can
                    // graft a result onto the dead session.
                    sessionGeneration++
                    wipeLocked()
                    val reason = if (result.code == AuthErrorCode.SESSION_REVOKED) {
                        AuthState.Reason.SESSION_REVOKED
                    } else {
                        AuthState.Reason.SESSION_EXPIRED
                    }
                    mAuthState.value = AuthState.Unauthenticated(reason, result.sessionEndReason)
                    RefreshOutcome.AuthFailed
                }
                is AuthNetworkResult.Transient -> RefreshOutcome.Transient(result.cause)
            }
        }
    }

    override suspend fun expireSession(seenAccessToken: String?) = mutex.withLock {
        val live = mAuthState.value is AuthState.Authenticated &&
            (seenAccessToken == null || currentAccessToken() == seenAccessToken)
        if (live) {
            sessionGeneration++
            wipeLocked()
            mAuthState.value = AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED)
        }
    }

    // endregion

    // region keep-alive (activity-gated, ADR-0009)

    private fun recordTokenLifetimeLocked(expiresInSeconds: Long) {
        val now = clock.elapsedRealtimeMillis()
        lastCommitAt = now
        accessExpiresAt = now + expiresInSeconds * 1_000
    }

    /** A new identity: replace the loop. Call under [mutex]. */
    private fun restartKeepAliveLocked() {
        if (!proactiveConfig.enabled) return
        proactiveJob?.cancel()
        proactiveJob = scope.launch(dispatchers.default) { keepAliveLoop() }
    }

    /** Start a loop only if none runs — so the loop's own refresh does not cancel it. Call under [mutex]. */
    private fun ensureKeepAliveLocked() {
        if (!proactiveConfig.enabled) return
        if (proactiveJob?.isActive != true) proactiveJob = scope.launch(dispatchers.default) { keepAliveLoop() }
    }

    /**
     * Sleeps until shortly before the access token lapses; then refreshes **only if the user interacted since the
     * last commit**. Otherwise it waits on the activity tracker — the client never ends an idle session, it only
     * stops extending it, and the server's idle window decides. The user's first touch wakes the loop (which
     * refreshes at once if the token lapsed meanwhile); their first request would refresh reactively anyway.
     *
     * Waiting, not returning: a loop that returned at an idle checkpoint could never consult the gate again, so a
     * returning user could tap forever with no refresh until a request failed.
     */
    private suspend fun keepAliveLoop() {
        while (currentCoroutineContext().isActive) {
            val expiresAt = accessExpiresAt
            val committedAt = lastCommitAt
            if (expiresAt <= 0L) return // no live session

            val lead = proactiveConfig.leadMillis.coerceAtMost((expiresAt - committedAt) / 2)
            val untilCheckpoint = expiresAt - lead - clock.elapsedRealtimeMillis()
            if (untilCheckpoint > 0) {
                delay(untilCheckpoint)
                continue // re-read: a rotation may have moved the expiry
            }

            if (activityTracker.lastInteractionAtMillis() <= committedAt) {
                activityTracker.interactions.first { it > committedAt }
                continue
            }

            when (refresh(currentAccessToken())) {
                is RefreshOutcome.Refreshed -> Unit // the commit moved the expiry; loop on
                is RefreshOutcome.Transient -> delay(proactiveConfig.retryDelayMillis)
                RefreshOutcome.AuthFailed, RefreshOutcome.Stale -> return
            }
        }
    }

    // endregion

    // region cold start

    override suspend fun resolveSession(): SessionResolution {
        if (mAuthState.value is AuthState.Authenticated) return SessionResolution.AUTHENTICATED

        val userId = store.currentUserId()
        if (userId == null) {
            setIfInitializing(AuthState.Unauthenticated(AuthState.Reason.NO_SESSION))
            return SessionResolution.UNAUTHENTICATED
        }
        val stored = store.readRefreshToken(userId)
        if (stored == null) {
            // A user without a readable token (key lost, tampered data): never crash, degrade to a dead session.
            mutex.withLock {
                wipeLocked()
                if (mAuthState.value is AuthState.Initializing) {
                    mAuthState.value = AuthState.Unauthenticated(AuthState.Reason.SESSION_EXPIRED)
                }
            }
            return SessionResolution.UNAUTHENTICATED
        }
        // A login may have landed while the store was read — it is authoritative; do not rotate its fresh token.
        if (mAuthState.value is AuthState.Authenticated) return SessionResolution.AUTHENTICATED

        mutex.withLock {
            if (refreshTokenInMemory == null) refreshTokenInMemory = stored
        }
        currentUserInMemory = profileStore.read(userId)
        // Transient leaves the state Initializing: never a false "no session" on a flaky network.
        return when (refresh(seenAccessToken = null)) {
            is RefreshOutcome.Refreshed -> SessionResolution.AUTHENTICATED
            RefreshOutcome.AuthFailed -> SessionResolution.UNAUTHENTICATED
            is RefreshOutcome.Transient -> SessionResolution.CONNECTION_ERROR
            RefreshOutcome.Stale ->
                if (mAuthState.value is AuthState.Authenticated) {
                    SessionResolution.AUTHENTICATED
                } else {
                    SessionResolution.UNAUTHENTICATED
                }
        }
    }

    private suspend fun setIfInitializing(state: AuthState) = mutex.withLock {
        if (mAuthState.value is AuthState.Initializing) mAuthState.value = state
    }

    // endregion

    /**
     * Clears tokens from memory and disk. Call under [mutex]. Keeps the non-secret profile cache (several users
     * may share a device). Cancels the keep-alive **last**: this runs from inside that loop when its refresh is
     * refused, and cancelling first would abort the suspending clear.
     */
    private suspend fun wipeLocked() {
        refreshTokenInMemory = null
        currentUserInMemory = null
        accessExpiresAt = 0L
        store.clear()
        proactiveJob?.cancel()
        proactiveJob = null
    }

    private companion object {
        /** Remote revoke attempts before giving up (best-effort; the local wipe never waits for it). */
        const val MAX_REVOKE_ATTEMPTS = 3
    }
}
