package {{APP_PACKAGE}}.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import {{ROOT_PACKAGE}}.files.config.FilesConfig
import {{ROOT_PACKAGE}}.network.config.NetworkConfig
import javax.inject.Singleton

/**
 * The host's configuration of `:files` (the host owns every infra config, MOD-15; mapping one infra module's config
 * onto another's is host work, MOD-16).
 *
 * Files are served from the API host today, so the files base URL is the network component's. When files move to
 * a CDN or bucket, change this one provider (e.g. a `FILES_BASE_URL` build-config field per build type).
 */
@Module
@InstallIn(SingletonComponent::class)
object FilesConfigModule {

    @Provides
    @Singleton
    fun provideFilesConfig(network: NetworkConfig): FilesConfig = FilesConfig(baseUrl = network.baseUrl)
}
