package {{ROOT_PACKAGE}}.files.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.OkHttpClient
import {{ROOT_PACKAGE}}.files.BuildConfig
import {{ROOT_PACKAGE}}.files.FilesAuthorizer
import {{ROOT_PACKAGE}}.files.internal.FilesAuthInterceptor
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsRepository
import {{ROOT_PACKAGE}}.network.log.reporterHttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The files module's own OkHttp client (ADR: separate clients per concern). It never shares the business
 * `@ApiClient`: image fetches must not trigger the API's 401 → refresh → retry machinery, and a CDN host may need
 * different credentials than the API.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FilesClient

/**
 * DI for `:files`.
 *
 * Deliberately **unbound** here: `FilesConfig` (the host provides it) and any [FilesAuthorizer] (declared
 * `@Multibinds`, so the client works with none; the session integration or the host contributes one).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FilesModule {

    @Multibinds
    abstract fun filesAuthorizers(): Set<FilesAuthorizer>

    companion object {

        private const val TIMEOUT_SECONDS = 15L

        /** A plain function, so a test builds exactly the production client. */
        @Provides
        @Singleton
        @FilesClient
        fun provideFilesOkHttpClient(
            authorizers: Set<@JvmSuppressWildcards FilesAuthorizer>,
            httpLogSettings: HttpLogSettingsRepository,
        ): OkHttpClient {
            check(authorizers.size <= 1) {
                "more than one FilesAuthorizer is bound (${authorizers.map { it::class.java.name }}); bind exactly one"
            }
            return OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(FilesAuthInterceptor(authorizers.firstOrNull()))
                .apply {
                    // Debug only, and after the authorizer, so a failed image load shows its headers and status.
                    if (BuildConfig.DEBUG) addInterceptor(reporterHttpLoggingInterceptor("OkHttp-Files", httpLogSettings))
                }
                .build()
        }
    }
}
