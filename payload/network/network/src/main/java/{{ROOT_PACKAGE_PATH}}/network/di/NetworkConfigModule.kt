package {{ROOT_PACKAGE}}.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import {{ROOT_PACKAGE}}.network.BuildConfig
import {{ROOT_PACKAGE}}.network.config.NetworkConfig
import javax.inject.Singleton

/**
 * The **one** place the backend environment enters the object graph.
 *
 * MOD-15 wants the environment owned by a single place and never defaulted inside a library. Here that single
 * place is `network/build.gradle.kts` (`BASE_URL` per build type, rendered from the installer's base-URL
 * answers): this provider only reads it, and every module that needs the URL injects [NetworkConfig] instead of
 * carrying its own. It lives in this module rather than in `:app` so the component installs without editing host
 * files; moving it to `:app/di/` (e.g. to choose the URL per product flavor) is a one-file move — delete this
 * one so the binding stays unique.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkConfigModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig = NetworkConfig(baseUrl = BuildConfig.BASE_URL)
}
