package {{ROOT_PACKAGE}}.auth.support

import {{ROOT_PACKAGE}}.auth.SessionUser
import {{ROOT_PACKAGE}}.auth.token.ProfileStore

/** In-memory [ProfileStore], per user, so "profiles survive a user switch" can be asserted. */
class FakeProfileStore : ProfileStore {

    private val byUser = mutableMapOf<String, SessionUser>()

    fun seed(user: SessionUser) {
        byUser[user.id] = user
    }

    fun snapshot(): Map<String, SessionUser> = byUser.toMap()

    override suspend fun read(userId: String): SessionUser? = byUser[userId]

    override suspend fun save(user: SessionUser) {
        byUser[user.id] = user
    }

    override suspend fun clear(userId: String) {
        byUser.remove(userId)
    }
}
