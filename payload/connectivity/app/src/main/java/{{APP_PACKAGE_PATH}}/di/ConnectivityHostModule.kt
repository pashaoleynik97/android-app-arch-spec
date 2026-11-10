package {{APP_PACKAGE}}.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import {{APP_PACKAGE}}.connectivity.ConnectivityBannerExtension
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension

/** Contributes the connectivity banner layer (spans `:connectivity` and the host chrome, so it lives here). */
@Module
@InstallIn(SingletonComponent::class)
abstract class ConnectivityHostModule {

    @Binds
    @IntoSet
    abstract fun bindConnectivityBannerExtension(impl: ConnectivityBannerExtension): HostExtension
}
