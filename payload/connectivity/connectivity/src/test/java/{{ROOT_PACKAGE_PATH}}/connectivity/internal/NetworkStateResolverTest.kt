package {{ROOT_PACKAGE}}.connectivity.internal

import org.junit.Assert.assertEquals
import org.junit.Test
import {{ROOT_PACKAGE}}.connectivity.NetworkState

/** The full truth table: link × probe answer. */
class NetworkStateResolverTest {

    @Test
    fun `every combination resolves as specified`() {
        val table = listOf(
            Triple(LinkStatus.NONE, null, NetworkState.OFFLINE),
            Triple(LinkStatus.NONE, true, NetworkState.OFFLINE),
            Triple(LinkStatus.NONE, false, NetworkState.OFFLINE),
            Triple(LinkStatus.UP, null, NetworkState.UNKNOWN),
            Triple(LinkStatus.UP, true, NetworkState.ONLINE),
            Triple(LinkStatus.UP, false, NetworkState.SERVER_UNREACHABLE),
        )
        table.forEach { (link, reachable, expected) ->
            assertEquals("$link / $reachable", expected, NetworkStateResolver.resolve(link, reachable))
        }
    }
}
