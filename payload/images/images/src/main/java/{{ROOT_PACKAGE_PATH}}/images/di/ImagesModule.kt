package {{ROOT_PACKAGE}}.images.di

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import {{ROOT_PACKAGE}}.files.di.FilesClient
import javax.inject.Singleton

/**
 * The app's one Coil [ImageLoader], fetching through the authenticated `@FilesClient` — so every `AsyncImage` in the
 * app (and every `{{THEME_PREFIX}}RemoteImage`) sends the files credentials without the call site knowing about them.
 * One loader means one memory and one disk cache.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImagesModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @FilesClient client: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .build()
}
