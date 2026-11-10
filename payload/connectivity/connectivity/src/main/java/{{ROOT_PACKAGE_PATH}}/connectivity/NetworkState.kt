package {{ROOT_PACKAGE}}.connectivity

/**
 * Can the app reach its backend right now? Published by [NetworkStateWatch].
 *
 * - [UNKNOWN] — not determined yet (start-up, or a link came up and the probe has not answered).
 * - [ONLINE] — a link with internet and the health probe answered 2xx.
 * - [SERVER_UNREACHABLE] — a link is up but the probe failed (server down, captive portal, DNS, proxy).
 * - [OFFLINE] — no link with internet at all.
 *
 * An enum because no state carries data; promote to a sealed type only when one must.
 */
enum class NetworkState { UNKNOWN, ONLINE, SERVER_UNREACHABLE, OFFLINE }
