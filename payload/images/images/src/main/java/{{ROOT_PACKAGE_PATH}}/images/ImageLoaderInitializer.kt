package {{ROOT_PACKAGE}}.images

import android.content.Context
import androidx.startup.Initializer
import coil3.ImageLoader
import coil3.SingletonImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Makes the Hilt-provided, authenticated [ImageLoader] Coil's **singleton**, so plain `AsyncImage(model = url)`
 * anywhere in the app uses it.
 *
 * Why this shape (Coil 3):
 * - Coil resolves its singleton lazily on first use: from a factory set with `SingletonImageLoader.setSafe`, else
 *   from an `Application` implementing `SingletonImageLoader.Factory`, else a default unauthenticated loader.
 *   Implementing the factory on `App` would mean editing the host; `setSafe` from here does not.
 * - `androidx.startup` runs this before `Application.onCreate` (its provider is merged from this module's
 *   manifest), i.e. before any composable can ask for an image — also in a process started without UI.
 * - The factory is **lazy**: the Hilt graph is touched only when the first image loads, through an [EntryPoint]
 *   (an initializer is created by the platform, not by Hilt). `setSafe` never replaces a loader that already exists.
 *
 * If the host removes `androidx.startup.InitializationProvider` from its merged manifest, call
 * `SingletonImageLoader.setSafe { … }` from `App.onCreate` instead.
 */
class ImageLoaderInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val app = context.applicationContext
        SingletonImageLoader.setSafe {
            EntryPointAccessors.fromApplication(app, ImageLoaderEntryPoint::class.java).imageLoader()
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageLoaderEntryPoint {
        fun imageLoader(): ImageLoader
    }
}
