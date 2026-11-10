package {{ROOT_PACKAGE}}.auth.token

/**
 * Persistence seam for the session. **Only the refresh token** is persisted (encrypted at rest); the access
 * token lives in memory. The real implementation is the only part that needs a device; every session and
 * concurrency rule is tested against an in-memory fake.
 *
 * Main-safe, and never throws on corrupt or undecryptable data — [readRefreshToken] returns `null`, which the
 * session reads as "no valid session" (KERNEL-17).
 */
interface SecureTokenStore {

    /** The id of the last signed-in user, or `null` when nothing is stored. */
    suspend fun currentUserId(): String?

    /** The decrypted refresh token of [userId], or `null` when absent or unreadable. Never throws. */
    suspend fun readRefreshToken(userId: String): String?

    /** Encrypts and stores [refreshToken] for [userId], and records [userId] as the current user. */
    suspend fun save(userId: String, refreshToken: String)

    /** Removes everything this store holds. */
    suspend fun clear()
}
