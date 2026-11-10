package {{APP_PACKAGE}}.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import {{ROOT_PACKAGE}}.libandroid.host.HostActivityObserver
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension

/**
 * Declares the host's extension sets so they resolve **empty** when no optional module contributes to them —
 * a Core-only install has none, and Hilt would otherwise fail on the missing binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HostExtensionsModule {

    @Multibinds
    abstract fun hostExtensions(): Set<HostExtension>

    @Multibinds
    abstract fun hostActivityObservers(): Set<HostActivityObserver>
}
