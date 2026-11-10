package {{ROOT_PACKAGE}}.connectivity.support

import kotlinx.coroutines.CompletableDeferred
import {{ROOT_PACKAGE}}.connectivity.internal.ServerHealthProbe

/** Scriptable [ServerHealthProbe]: [reachable] answers, [calls] counts, [gate] holds a probe in flight. */
class FakeServerHealthProbe : ServerHealthProbe {
    var reachable = true
    var calls = 0
        private set
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun isServerReachable(): Boolean {
        calls++
        gate?.await()
        return reachable
    }
}
