package {{ROOT_PACKAGE}}.auth.access

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.auth.AuthState
import {{ROOT_PACKAGE}}.auth.SessionEndReason
import {{ROOT_PACKAGE}}.auth.di.AuthScope
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import {{ROOT_PACKAGE}}.libcommon.time.TimeSource
import {{ROOT_PACKAGE}}.network.access.AccessLoss
import {{ROOT_PACKAGE}}.network.access.AccessLossBus
import {{ROOT_PACKAGE}}.network.access.AccessLossConfig
import {{ROOT_PACKAGE}}.network.access.RecoveryBudget
import {{ROOT_PACKAGE}}.network.access.ScopeRecovery
import javax.inject.Inject
import javax.inject.Singleton

/** What the host tells the user after a refusal nobody else handled, while they stay where they are. */
enum class AccessNotice {
    Forbidden,
}

/**
 * The last link of the access-loss chain — whatever no screen and no feature filter consumed ends here, and here
 * it is always consumed:
 *
 * - [AccessLoss.Forbidden] → a "no permission" notice ([notice]); the user stays put — moving them would be worse
 *   than the refusal of one action.
 * - [AccessLoss.ScopeLost] → the app's [ScopeRecovery] (when one is bound and the [RecoveryBudget] allows);
 *   otherwise the session ends with [SessionEndReason.ACCESS_CHANGED], and the session host extension takes the
 *   user to sign-in with the access-changed notice. Losing the scope the tokens are bound to *is* losing the
 *   session — staying signed in would only earn the next 403.
 *
 * Installs itself as the bus fallback when constructed (the access host extension in `:app` injects it, so it
 * exists as soon as the activity does). A session boundary clears the notice and refills the budget.
 */
@Singleton
class AccessLossFallback @Inject constructor(
    bus: AccessLossBus,
    private val authManager: AuthManager,
    private val recoveries: Set<@JvmSuppressWildcards ScopeRecovery>,
    config: AccessLossConfig,
    clock: TimeSource,
    @param:AuthScope scope: CoroutineScope,
) {
    private val logTag = logTag()

    private val budget = RecoveryBudget(config.maxRecoveries, config.recoveryWindow, clock)

    private val mNotice = MutableStateFlow<AccessNotice?>(null)
    val notice: StateFlow<AccessNotice?> = mNotice.asStateFlow()

    init {
        bus.setFallback(::handle)
        scope.launch {
            authManager.authState.collect { state ->
                if (state is AuthState.Unauthenticated) {
                    // A notice belongs to a live session: a dead one supersedes it (the user is going to sign-in).
                    mNotice.value = null
                    budget.reset()
                }
            }
        }
    }

    internal suspend fun handle(loss: AccessLoss): Boolean {
        when (loss) {
            is AccessLoss.Forbidden -> mNotice.value = AccessNotice.Forbidden
            is AccessLoss.ScopeLost -> {
                val recovery = recoveries.firstOrNull()
                val recovered = recovery != null && budget.tryAcquire() && recovery.recover(loss)
                if (!recovered) {
                    if (recovery != null) Reporter.error("scope recovery exhausted or failed: $loss", where = logTag)
                    authManager.endSession(SessionEndReason.ACCESS_CHANGED)
                }
            }
        }
        return true
    }

    fun dismissNotice() {
        mNotice.value = null
    }
}
