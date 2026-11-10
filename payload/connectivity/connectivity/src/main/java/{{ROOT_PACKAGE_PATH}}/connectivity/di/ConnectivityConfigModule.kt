package {{ROOT_PACKAGE}}.connectivity.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import {{ROOT_PACKAGE}}.connectivity.BuildConfig
import {{ROOT_PACKAGE}}.connectivity.config.ConnectivityConfig
import javax.inject.Singleton

/**
 * The one provider of [ConnectivityConfig], read from `connectivity/build.gradle.kts` (MOD-15: that build file is the
 * single place the probe's environment is set). Move it to `:app/di/` to derive the URL from the host instead.
 */
@Module
@InstallIn(SingletonComponent::class)
object ConnectivityConfigModule {

    @Provides
    @Singleton
    fun provideConnectivityConfig(): ConnectivityConfig =
        ConnectivityConfig(baseUrl = BuildConfig.BASE_URL, healthPath = BuildConfig.HEALTH_PATH)
}
