package {{ROOT_PACKAGE}}.connectivity.internal

/** Seam over the backend health check. */
interface ServerHealthProbe {
    /** `true` for a 2xx; `false` for anything else, including IO failures. Never throws for IO. Main-safe. */
    suspend fun isServerReachable(): Boolean
}
