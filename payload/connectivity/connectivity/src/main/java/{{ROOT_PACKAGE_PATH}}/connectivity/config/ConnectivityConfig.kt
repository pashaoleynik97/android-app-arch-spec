package {{ROOT_PACKAGE}}.connectivity.config

/**
 * Where the health probe goes: `baseUrl + healthPath`. No defaults (DI-08) — provided once, by
 * `ConnectivityConfigModule`, from this module's `BuildConfig`, which its build file sets per build type.
 *
 * @param baseUrl the backend root, ending with `/`.
 * @param healthPath relative to [baseUrl], no leading slash (`health`, `v1/health`).
 */
data class ConnectivityConfig(val baseUrl: String, val healthPath: String) {
    init {
        require(baseUrl.endsWith("/")) { "ConnectivityConfig.baseUrl must end with '/' (was '$baseUrl')" }
    }

    val healthUrl: String get() = baseUrl + healthPath.trimStart('/')
}
