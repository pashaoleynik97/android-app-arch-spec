package {{APP_PACKAGE}}.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import {{APP_PACKAGE}}.access.AccessLossHostExtension
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension

/** Contributes the access-loss notice layer to the host (spans `:auth` and the host chrome, so it lives here). */
@Module
@InstallIn(SingletonComponent::class)
abstract class AccessLossHostModule {

    @Binds
    @IntoSet
    abstract fun bindAccessLossHostExtension(impl: AccessLossHostExtension): HostExtension
}
