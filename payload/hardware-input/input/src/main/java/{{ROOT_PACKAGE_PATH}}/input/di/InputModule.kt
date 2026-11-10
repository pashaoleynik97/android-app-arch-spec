package {{ROOT_PACKAGE}}.input.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import {{ROOT_PACKAGE}}.input.InputController
import {{ROOT_PACKAGE}}.input.api.InputDriverProvider
import {{ROOT_PACKAGE}}.input.settings.InputSettingsStore
import {{ROOT_PACKAGE}}.input.settings.SharedPrefsInputSettingsStore
import {{ROOT_PACKAGE}}.libandroid.host.HostActivityObserver
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import javax.inject.Qualifier
import javax.inject.Singleton

/** The dispatcher the input settings file is read and written on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InputIoDispatcher

/**
 * App-lifetime scope for the controller's driver/lifecycle machinery. **Main-dispatched**: the Activity lifecycle
 * callbacks that drive it arrive on the main thread, and a release must not lag behind a pause.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InputScope

/**
 * DI for `:input`.
 *
 * It **declares** the [InputDriverProvider] multibinding but contributes nothing to it — each vendor module (and
 * `:input-debug` in debug builds) binds its own provider with `@Binds @IntoSet`. [Multibinds] keeps the set
 * injectable in a build that contains no driver at all (a release build of an app with no vendor module).
 *
 * The controller is contributed to the host's `Set<HostActivityObserver>`, so `MainActivity` registers it on its
 * lifecycle without knowing this module exists.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InputModule {

    @Binds
    internal abstract fun bindInputSettingsStore(impl: SharedPrefsInputSettingsStore): InputSettingsStore

    @Multibinds
    abstract fun inputDriverProviders(): Set<InputDriverProvider>

    @Binds
    @IntoSet
    abstract fun bindControllerAsHostObserver(controller: InputController): HostActivityObserver

    companion object {

        @Provides
        @Singleton
        @InputIoDispatcher
        fun provideInputIoDispatcher(dispatchers: DispatcherProvider): CoroutineDispatcher = dispatchers.io

        @Provides
        @Singleton
        @InputScope
        fun provideInputScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
