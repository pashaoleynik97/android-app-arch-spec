package {{ROOT_PACKAGE}}.auth.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.di.AuthScope
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The host's view of the session, kept outside any screen and any activity: [destination] (where the user
 * belongs) and [notice] (why a live session just ended). The session host extension in `:app` renders the
 * notice and applies [SessionPolicy.route] to its `NavController`; this class has no UI, so it is JVM-tested.
 *
 * Observes on the app-lifetime [AuthScope] from construction, so a session that dies while no activity exists
 * still leaves its notice for the next one. Features never react to a dead session themselves — they swallow
 * their `SessionExpired` rung because this is already navigating (DATA-18).
 */
@Singleton
class SessionShell @Inject constructor(
    authManager: AuthManager,
    @param:AuthScope private val scope: CoroutineScope,
) {

    private val logTag = logTag()

    val destination: StateFlow<SessionDestination> = authManager.authState
        .map(SessionPolicy::destination)
        .stateIn(scope, SharingStarted.Eagerly, SessionPolicy.destination(authManager.authState.value))

    private val mNotice = MutableStateFlow<SessionNotice?>(null)
    val notice: StateFlow<SessionNotice?> = mNotice.asStateFlow()

    init {
        scope.launch {
            var previous: AuthState? = null
            authManager.authState.collect { current ->
                when {
                    // A new session clears whatever notice the previous one left.
                    current is AuthState.Authenticated && previous !is AuthState.Authenticated -> mNotice.value = null
                    else -> SessionPolicy.noticeFor(previous, current)?.let { notice ->
                        Reporter.info("session ended: $notice", where = logTag)
                        mNotice.value = notice
                    }
                }
                previous = current
            }
        }
    }

    /** The user closed the notice, or it timed out. */
    fun dismissNotice() {
        mNotice.value = null
    }
}
