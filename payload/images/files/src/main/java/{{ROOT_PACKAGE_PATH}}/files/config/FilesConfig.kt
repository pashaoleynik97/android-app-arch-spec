package {{ROOT_PACKAGE}}.files.config

/**
 * Where server-stored files (avatars, attachments, photos) are fetched from. Provided by the host (`:app`'s
 * `FilesConfigModule`, from the network component's base URL) — this module never defaults the host (MOD-15).
 *
 * A config of its own, distinct from the API's, even when both point at the same host today: files are often moved
 * to a CDN or a storage bucket later, and then only this value changes.
 *
 * @param baseUrl scheme + host (+ base path), with or without a trailing `/`.
 * @param filesPath the path files are served under, relative to [baseUrl], ending in `/`. `v1/files/` is the
 *   example contract's — adapt it to your backend.
 */
data class FilesConfig(
    val baseUrl: String,
    val filesPath: String = DEFAULT_FILES_PATH,
) {
    companion object {
        const val DEFAULT_FILES_PATH = "v1/files/"
    }
}
