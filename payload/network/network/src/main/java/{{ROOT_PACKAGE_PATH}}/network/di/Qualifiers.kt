package {{ROOT_PACKAGE}}.network.di

import javax.inject.Qualifier

/**
 * The **business** OkHttp client — every feature endpoint goes through it. Optional modules decorate it through
 * DI ([{{ROOT_PACKAGE}}.network.ApiInterceptor] set, and an `@ApiClient Authenticator` set of at most one), never by
 * editing this module. Also qualifies that authenticator set.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiClient

/**
 * Retrofit over the [ApiClient] client. Qualified because other modules (the session's own client, an image
 * client) provide their own Retrofit / OkHttp; two unqualified bindings of one type collide in `:app` (DI-12).
 * Feature domain modules build their APIs from it: `@Provides fun api(@ApiRetrofit r: Retrofit): XApi = r.create()`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApiRetrofit

/** App-lifetime scope of this module (`SupervisorJob + Default`): the logging-policy load and similar one-shots. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NetworkScope
