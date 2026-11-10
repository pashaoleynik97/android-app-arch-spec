package {{ROOT_PACKAGE}}.network.access

/**
 * App-wide chain of responsibility for [AccessLoss] (ADR-0005: a `Channel`, not a `SharedFlow`).
 *
 * A loss is offered, in order, to: the **registered** handlers, last-registered first (normally the screen in
 * front, which may keep a refusal local); then the **standing filters** (`Set<AccessLossFilter>`, contributed with
 * `@IntoSet` by feature modules and alive for the whole process); then the host's [setFallback] handler, which
 * always consumes. The first handler that returns `true` stops the chain.
 */
interface AccessLossBus {

    /** Report a refusal. Non-suspending and never blocking — it is called from OkHttp's threads. */
    fun report(loss: AccessLoss)

    /**
     * Register a handler while the caller is active; cancel the [Registration] when it stops being active — a
     * handler that outlives its screen swallows losses meant for the host.
     */
    fun register(handler: AccessLossHandler): Registration

    /** The host's terminal handler; set once at startup (a later call replaces it). */
    fun setFallback(handler: AccessLossHandler)

    /** Undoes a [register]. Idempotent. */
    fun interface Registration {
        fun cancel()
    }
}

/** One link of the chain. Return `true` only when the loss is **fully dealt with** — the host will never see it. */
fun interface AccessLossHandler {
    suspend fun handle(loss: AccessLoss): Boolean
}

/**
 * A handler that lives for the whole process, contributed by a feature module with `@Binds @IntoSet`.
 *
 * The way for a feature to swallow its **expected** 403s (e.g. "this item is read-only for you" on its own detail
 * endpoint) so the host does not add a generic notice on top of the feature's own message. Process-wide rather
 * than per screen because the 403 arrives on a background thread and would race a screen-scoped registration;
 * standing in the bus from its construction means none is missed (DATA-62).
 */
interface AccessLossFilter : AccessLossHandler
