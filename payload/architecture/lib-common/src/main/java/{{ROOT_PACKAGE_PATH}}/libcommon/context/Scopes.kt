package {{ROOT_PACKAGE}}.libcommon.context

import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory holder that keeps flow-scoped models alive across the screens of one flow, so large objects
 * never travel through navigation arguments (routes carry ids only).
 *
 * A [Scope] is created lazily by [getOrOpenScope], replaced by [updateScope], read without creating by
 * [peekScope], read-and-closed in one step by [consumeScope], and destroyed by [closeScope] — typically from
 * the owning ViewModel's `onCleared()`.
 *
 * Features declare their own [ScopeKey]s and [Scope] types; this class never changes when a flow is added.
 *
 * **Not process-death safe.** The store lives in memory only, so a consumer MUST tolerate a missing scope
 * (fall back to a re-read, or step back). Anything that must survive process death belongs in a route
 * argument or in the back stack's saved state instead.
 *
 * All operations are synchronized.
 */
@Singleton
class Scopes @Inject constructor() {

    /** Marker for anything stored as a flow scope. */
    interface Scope

    /** Identifies one scope slot and knows how to create it. [id] MUST be unique across the app. */
    interface ScopeKey<T : Scope> {
        val id: String
        fun create(): T
    }

    private val store = mutableMapOf<String, Scope>()

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T : Scope> getOrOpenScope(key: ScopeKey<T>): T = store.getOrPut(key.id) { key.create() } as T

    @Synchronized
    fun <T : Scope> updateScope(key: ScopeKey<T>, scope: T) {
        store[key.id] = scope
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T : Scope> peekScope(key: ScopeKey<T>): T? = store[key.id] as? T

    /** Reads the scope for [key] (if any) and closes it — a one-shot handoff. */
    @Synchronized
    fun <T : Scope> consumeScope(key: ScopeKey<T>): T? = peekScope(key).also { closeScope(key) }

    @Synchronized
    fun isOpen(key: ScopeKey<*>): Boolean = store.containsKey(key.id)

    @Synchronized
    fun closeScope(key: ScopeKey<*>) {
        store.remove(key.id)
    }

    @Synchronized
    fun closeAll() = store.clear()
}
