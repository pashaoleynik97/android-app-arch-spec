package {{ROOT_PACKAGE}}.connectivity.support

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import {{ROOT_PACKAGE}}.connectivity.internal.ConnectivityMonitor
import {{ROOT_PACKAGE}}.connectivity.internal.LinkStatus

/** [ConnectivityMonitor] the test drives with [set]; replays the last link to a late collector. */
class FakeConnectivityMonitor : ConnectivityMonitor {

    private val flow = MutableSharedFlow<LinkStatus>(replay = 1)

    @Volatile
    private var currentLink = LinkStatus.NONE

    override val linkStatus: Flow<LinkStatus> = flow
    override fun current(): LinkStatus = currentLink

    suspend fun set(link: LinkStatus) {
        currentLink = link
        flow.emit(link)
    }
}
