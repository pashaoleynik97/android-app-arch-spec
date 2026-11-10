package {{APP_PACKAGE}}.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import {{APP_PACKAGE}}.session.SessionHostExtension
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension

/**
 * Contributes the session's host layer. Lives in `:app` because it spans the session (`:auth`), the sign-in
 * route (`:login-ui-api`) and the host chrome (`:lib-android`) — only the host sees all three (MOD-16).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionHostModule {

    @Binds
    @IntoSet
    abstract fun bindSessionHostExtension(impl: SessionHostExtension): HostExtension
}
