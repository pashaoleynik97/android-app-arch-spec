package {{ROOT_PACKAGE}}.auth.di

import javax.inject.Qualifier

/**
 * The session's own OkHttp client (login, refresh, logout). It installs **no** bearer interceptor and **no**
 * authenticator: a 401 on login or refresh must never trigger another refresh (no circular refresh).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

/** Retrofit over [AuthClient]. Qualified so it can never collide with another module's Retrofit (DI-12). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthRetrofit

/** App-lifetime scope (`SupervisorJob + Default`) hosting the shared refresh, the bootstrap and the keep-alive. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthScope
