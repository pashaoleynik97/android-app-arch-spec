package {{ROOT_PACKAGE}}.network.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import {{ROOT_PACKAGE}}.network.ApiInterceptor
import {{ROOT_PACKAGE}}.network.BuildConfig
import {{ROOT_PACKAGE}}.network.config.NetworkConfig
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsRepository
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsStore
import {{ROOT_PACKAGE}}.network.log.SharedPrefsHttpLogSettingsStore
import {{ROOT_PACKAGE}}.network.log.reporterHttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * DI for the business HTTP stack.
 *
 * **Extension seams** (both declared `@Multibinds`, so they resolve empty when nothing contributes — DI-13):
 * - `Set<ApiInterceptor>` — application interceptors from optional modules (credentials, response observers),
 *   applied in [ApiInterceptor.order];
 * - `@ApiClient Set<Authenticator>` — **at most one** authenticator (a session module's 401 → refresh → retry).
 *   A set rather than an optional binding so it works on every `minSdk`; more than one is a wiring error and
 *   fails fast at startup.
 *
 * Provided **once, here**: the app's single `Json` (DI-12, DATA-20) — every module reuses it.
 * Deliberately **not** here: [NetworkConfig] has its own module (`NetworkConfigModule`), the one place that
 * chooses the environment.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Multibinds
    abstract fun apiInterceptors(): Set<ApiInterceptor>

    @Multibinds
    @ApiClient
    abstract fun apiAuthenticators(): Set<Authenticator>

    @Binds
    internal abstract fun bindHttpLogSettingsStore(impl: SharedPrefsHttpLogSettingsStore): HttpLogSettingsStore

    companion object {

        private const val TIMEOUT_SECONDS = 20L

        /** Unknown fields ignored (the server ships fields before the app reads them); absent optionals stay default. */
        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        @Provides
        @Singleton
        @NetworkScope
        fun provideNetworkScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * The business client. A plain function (Hilt `@Provides` are ordinary functions), so tests build the
         * exact production client with their own interceptors and authenticator.
         */
        @Provides
        @Singleton
        @ApiClient
        fun provideApiOkHttpClient(
            interceptors: Set<@JvmSuppressWildcards ApiInterceptor>,
            @ApiClient authenticators: Set<@JvmSuppressWildcards Authenticator>,
            httpLogSettings: HttpLogSettingsRepository,
        ): OkHttpClient {
            check(authenticators.size <= 1) {
                "more than one @ApiClient Authenticator is bound (${authenticators.map { it::class.java.name }}); " +
                    "OkHttp takes exactly one — bind a single authenticator that delegates"
            }
            return OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .apply {
                    interceptors.sortedBy { it.order }.forEach { addInterceptor(it) }
                    authenticators.firstOrNull()?.let { authenticator(it) }
                    // Debug only, and last: the logged request shows the headers the contributors attached.
                    if (BuildConfig.DEBUG) addInterceptor(reporterHttpLoggingInterceptor("OkHttp-Api", httpLogSettings))
                }
                .build()
        }

        @Provides
        @Singleton
        @ApiRetrofit
        fun provideApiRetrofit(
            @ApiClient client: OkHttpClient,
            config: NetworkConfig,
            json: Json,
        ): Retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
