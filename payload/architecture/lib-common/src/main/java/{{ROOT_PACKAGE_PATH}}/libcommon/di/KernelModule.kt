package {{ROOT_PACKAGE}}.libcommon.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import {{ROOT_PACKAGE}}.libcommon.coroutines.DefaultDispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.libcommon.lifecycle.AppForegroundWatch
import {{ROOT_PACKAGE}}.libcommon.lifecycle.DefaultAppForegroundWatch
import {{ROOT_PACKAGE}}.libcommon.time.SystemClockTimeSource
import {{ROOT_PACKAGE}}.libcommon.time.TimeSource

/**
 * Binds the kernel seams once, app-wide. Other modules inject the interfaces; they never bind them again
 * (a second binding of the same type is a Hilt compile error).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class KernelModule {

    @Binds
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    abstract fun bindTimeSource(impl: SystemClockTimeSource): TimeSource

    @Binds
    abstract fun bindAppForegroundWatch(impl: DefaultAppForegroundWatch): AppForegroundWatch
}
