package {{ROOT_PACKAGE}}.connectivity.support

import kotlinx.coroutines.flow.MutableStateFlow
import {{ROOT_PACKAGE}}.connectivity.NetworkState
import {{ROOT_PACKAGE}}.connectivity.NetworkStateWatch

/** [NetworkStateWatch] whose state the test sets; counts [flush] calls. */
class FakeNetworkStateWatch : NetworkStateWatch {
    override val networkState = MutableStateFlow(NetworkState.UNKNOWN)
    var flushes = 0
        private set

    override fun getCurrentNetworkState(): NetworkState = networkState.value

    override fun flush() {
        flushes++
    }
}
