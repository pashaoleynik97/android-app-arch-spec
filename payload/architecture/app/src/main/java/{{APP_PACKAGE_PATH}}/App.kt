package {{APP_PACKAGE}}

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import {{ROOT_PACKAGE}}.libcommon.lifecycle.AppForegroundWatch
import javax.inject.Inject

/**
 * Application entry point and composition root.
 *
 * - `@HiltAndroidApp` bootstraps the dependency graph.
 * - Background singletons are **field-injected purely to exist**: anything that must run from process start
 *   (observers, caches that poll, filters that must be registered before the first user action) is listed
 *   here, so no screen is ever what first constructs it.
 * - [onCreate] registers every feature so `AppNavHost` can attach its destinations. Features whose screens
 *   lead into a sibling feature receive those routes here, as constructor arguments ("host-supplied seams"),
 *   because sibling `:x-ui` modules may not depend on each other.
 */
@HiltAndroidApp
class App : Application() {

    /** Must observe the process lifecycle from startup; background jobs gate on it. */
    @Inject
    lateinit var appForegroundWatch: AppForegroundWatch

    override fun onCreate() {
        super.onCreate()
        registerFeatures()
    }

    /** One line per feature. Scale-up path: a Hilt `@IntoSet FeatureApi` multibinding. */
    private fun registerFeatures() {
        // <arch-spec:feature-registrations>
        // </arch-spec:feature-registrations>
    }
}
