package {{ROOT_PACKAGE}}.auth.token

import {{ROOT_PACKAGE}}.auth.SessionUser

/**
 * Optional, non-secret cache of user profiles (name, email), keyed by user id — several users may share a device,
 * and signing in as another user does not erase the others. It **survives sign-out** (only tokens are wiped) so a
 * cold start can show who is signed in before the first network answer.
 *
 * Main-safe; never throws on corrupt data ([read] returns `null`).
 */
interface ProfileStore {
    suspend fun read(userId: String): SessionUser?
    suspend fun save(user: SessionUser)
    suspend fun clear(userId: String)
}
