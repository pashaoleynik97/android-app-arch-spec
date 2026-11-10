package {{ROOT_PACKAGE}}.media

import android.content.Context
import androidx.startup.Initializer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Starts [StaleMediaSweeper] at process start without touching the host's `App` class.
 *
 * `androidx.startup` runs this from its `InitializationProvider` (declared in this module's manifest and merged
 * into the app's), before `Application.onCreate`. The sweeper is reached through a Hilt [EntryPoint] because an
 * initializer is created by the platform, not by Hilt; Hilt builds the singleton component lazily on that first
 * access, so this is safe before `onCreate`.
 *
 * If the host ever removes `androidx.startup.InitializationProvider` from its merged manifest (some apps do, to
 * initialise WorkManager by hand), this stops running — call `StaleMediaSweeper.start()` from `App.onCreate`
 * instead.
 */
class MediaStartupInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        EntryPointAccessors.fromApplication(context.applicationContext, MediaStartupEntryPoint::class.java)
            .staleMediaSweeper()
            .start()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MediaStartupEntryPoint {
        fun staleMediaSweeper(): StaleMediaSweeper
    }
}
