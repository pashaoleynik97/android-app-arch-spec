package {{APP_PACKAGE}}

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import {{ROOT_PACKAGE}}.libandroid.host.HostActivityObserver
import {{ROOT_PACKAGE}}.libandroid.host.HostExtension
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}Theme
import {{ROOT_PACKAGE}}.libandroid.ui.theme.{{THEME_PREFIX}}ThemeMode

/** The single activity: edge-to-edge, the theme, and the host [AppNavHost]. Screens are destinations, not activities. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Host UI contributed by optional modules (empty on a Core-only install). See [HostExtension]. */
    @Inject
    lateinit var hostExtensions: Set<@JvmSuppressWildcards HostExtension>

    /** Activity-lifecycle hooks contributed by optional modules. See [HostActivityObserver]. */
    @Inject
    lateinit var hostActivityObservers: Set<@JvmSuppressWildcards HostActivityObserver>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hostActivityObservers.forEach(lifecycle::addObserver)
        if ({{THEME_PREFIX}}ThemeMode.supportsDark) {
            enableEdgeToEdge()
        } else {
            // A light-only app keeps dark system-bar icons regardless of the system dark setting; the default
            // auto() style would draw white icons over the light surfaces on a device in dark mode.
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            )
        }
        setContent {
            {{THEME_PREFIX}}Theme {
                AppNavHost(hostExtensions = hostExtensions, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
