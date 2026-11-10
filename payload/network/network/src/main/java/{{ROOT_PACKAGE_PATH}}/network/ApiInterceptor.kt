package {{ROOT_PACKAGE}}.network

import okhttp3.Interceptor

/**
 * An application interceptor an **optional** module adds to the business client ([{{ROOT_PACKAGE}}.network.di.ApiClient])
 * without this module knowing it: contribute it with `@Binds @IntoSet` from the contributing module's Hilt module.
 * The set is declared `@Multibinds`, so a project without contributors still builds.
 *
 * Contributors run **in ascending [order]**, before the debug logging interceptor (which is always last, so a
 * logged request shows the headers actually sent). Every application interceptor sees the **final** response —
 * after the authenticator's retry — because OkHttp runs authenticators below the application interceptors.
 *
 * Bands (leave gaps so later modules can slot in):
 * - [ORDER_CREDENTIALS] — attaches credentials (a bearer token, a tenant header);
 * - [ORDER_OBSERVERS] — looks at responses without changing them (access-loss reporting, metrics).
 *
 * An interceptor that reads a body MUST use `peekBody(limit)`, never consume it: the service classifying the
 * same response reads the single-read error body afterwards (DATA-14).
 */
interface ApiInterceptor : Interceptor {

    val order: Int

    companion object {
        const val ORDER_CREDENTIALS = 100
        const val ORDER_OBSERVERS = 200
    }
}
