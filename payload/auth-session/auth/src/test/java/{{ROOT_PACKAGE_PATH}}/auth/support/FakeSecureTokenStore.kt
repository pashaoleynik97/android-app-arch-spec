package {{ROOT_PACKAGE}}.auth.support

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import {{ROOT_PACKAGE}}.auth.token.SecureTokenStore

/**
 * In-memory [SecureTokenStore] — the reason the storage seam exists: every session rule runs on the JVM.
 * [failReads] simulates a lost Keystore key or tampered ciphertext; [clearGate] holds `clear()` (which the
 * manager calls under its lock) so a test can reproduce a contended lock deterministically.
 */
class FakeSecureTokenStore : SecureTokenStore {

    private val lock = Mutex()
    private val tokensByUser = mutableMapOf<String, String>()
    private var current: String? = null

    var failReads = false
    var clearGate: CompletableDeferred<Unit>? = null

    var saveCount = 0
        private set
    var clearCount = 0
        private set

    fun seed(userId: String, refreshToken: String) {
        tokensByUser[userId] = refreshToken
        current = userId
    }

    fun snapshot(): Map<String, String> = tokensByUser.toMap()

    override suspend fun currentUserId(): String? = lock.withLock { current }

    override suspend fun readRefreshToken(userId: String): String? = lock.withLock {
        if (failReads) null else tokensByUser[userId]
    }

    override suspend fun save(userId: String, refreshToken: String) = lock.withLock {
        saveCount++
        tokensByUser[userId] = refreshToken
        current = userId
    }

    override suspend fun clear() {
        clearGate?.await()
        lock.withLock {
            clearCount++
            tokensByUser.clear()
            current = null
        }
    }
}
