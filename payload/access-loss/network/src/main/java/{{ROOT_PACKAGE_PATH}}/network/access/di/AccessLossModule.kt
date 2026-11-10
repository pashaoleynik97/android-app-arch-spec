package {{ROOT_PACKAGE}}.network.access.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import {{ROOT_PACKAGE}}.network.ApiInterceptor
import {{ROOT_PACKAGE}}.network.access.AccessLossBus
import {{ROOT_PACKAGE}}.network.access.AccessLossConfig
import {{ROOT_PACKAGE}}.network.access.AccessLossFilter
import {{ROOT_PACKAGE}}.network.access.AccessLossInterceptor
import {{ROOT_PACKAGE}}.network.access.DefaultAccessLossBus
import {{ROOT_PACKAGE}}.network.access.ScopeRecovery
import javax.inject.Singleton

/**
 * DI for the access-loss chain. Adds [AccessLossInterceptor] to the business client through the network module's
 * `ApiInterceptor` set — the network module's code is not touched.
 *
 * Left **unbound** on purpose (DI-09): `Set<AccessLossFilter>` (features contribute their standing filters) and
 * `Set<ScopeRecovery>` (the app may contribute one way to recover a lost scope). Both are `@Multibinds`, so they
 * resolve empty.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AccessLossModule {

    @Binds
    abstract fun bindAccessLossBus(impl: DefaultAccessLossBus): AccessLossBus

    @Binds
    @IntoSet
    abstract fun contributeAccessLossInterceptor(impl: AccessLossInterceptor): ApiInterceptor

    @Multibinds
    abstract fun accessLossFilters(): Set<AccessLossFilter>

    @Multibinds
    abstract fun scopeRecoveries(): Set<ScopeRecovery>

    companion object {
        /** Example-contract defaults; replace this provider to adapt the scope-level codes or the budget. */
        @Provides
        @Singleton
        fun provideAccessLossConfig(): AccessLossConfig = AccessLossConfig()
    }
}
