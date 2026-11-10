package {{ROOT_PACKAGE}}.network.config

/**
 * The business API's base URL. A config class with **no default**: a missing binding fails the build instead
 * of silently pointing a release at a placeholder host (DI-08).
 *
 * Provided once, by `di/NetworkConfigModule`, from this module's `BuildConfig.BASE_URL` — which the module's
 * build file sets per build type from the installer's base-URL answers. Every other module that talks to the
 * same backend (session, files) injects this instead of declaring its own URL, so there is exactly one place
 * where the environment is chosen.
 *
 * Paths in Retrofit interfaces are relative (`"v1/items"`), so [baseUrl] must end with `/`.
 */
data class NetworkConfig(val baseUrl: String) {
    init {
        require(baseUrl.endsWith("/")) { "NetworkConfig.baseUrl must end with '/' (was '$baseUrl')" }
    }
}
