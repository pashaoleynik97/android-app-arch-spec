package {{ROOT_PACKAGE}}.network.access

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tuning of the access-loss chain. Provided with these defaults by `AccessLossModule`; replace that provider to
 * adapt the **example contract**.
 *
 * @param scopeLevelCodes 403 error codes that mean "the user lost the whole scope the session is bound to" →
 *   [AccessLoss.ScopeLost]. Every other 403 is [AccessLoss.Forbidden]. The default is the example contract's code.
 * @param maxRecoveries how many automatic scope recoveries may happen inside [recoveryWindow] before the host
 *   gives up and ends the session. A recovery mints new tokens; a backend that keeps refusing them would otherwise
 *   loop forever, and nothing further down can notice (each round is a new request with a new token).
 * @param recoveryWindow the sliding window [maxRecoveries] is counted over.
 */
data class AccessLossConfig(
    val scopeLevelCodes: Set<String> = setOf("SCOPE_ACCESS_REVOKED"),
    val maxRecoveries: Int = 3,
    val recoveryWindow: Duration = 60.seconds,
)
