package {{ROOT_PACKAGE}}.auth.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import {{ROOT_PACKAGE}}.auth.AuthManager
import {{ROOT_PACKAGE}}.auth.BuildConfig
import {{ROOT_PACKAGE}}.auth.DefaultUserActivityTracker
import {{ROOT_PACKAGE}}.auth.UserActivityTracker
import {{ROOT_PACKAGE}}.auth.config.ProactiveRefreshConfig
import {{ROOT_PACKAGE}}.auth.internal.DefaultAuthManager
import {{ROOT_PACKAGE}}.auth.internal.RefreshCoordinator
import {{ROOT_PACKAGE}}.auth.network.AuthApi
import {{ROOT_PACKAGE}}.auth.network.AuthInterceptor
import {{ROOT_PACKAGE}}.auth.network.AuthService
import {{ROOT_PACKAGE}}.auth.network.RetrofitAuthService
import {{ROOT_PACKAGE}}.auth.network.TokenAuthenticator
import {{ROOT_PACKAGE}}.auth.token.KeystoreSecureTokenStore
import {{ROOT_PACKAGE}}.auth.token.ProfileStore
import {{ROOT_PACKAGE}}.auth.token.SecureTokenStore
import {{ROOT_PACKAGE}}.auth.token.SharedPrefsProfileStore
import {{ROOT_PACKAGE}}.network.ApiInterceptor
import {{ROOT_PACKAGE}}.network.config.NetworkConfig
import {{ROOT_PACKAGE}}.network.di.ApiClient
import {{ROOT_PACKAGE}}.network.log.HttpLogSettingsRepository
import {{ROOT_PACKAGE}}.network.log.reporterHttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * DI for the session.
 *
 * **Plugs into the business client** through the network module's seams — never by editing it:
 * [AuthInterceptor] into `Set<ApiInterceptor>`, [TokenAuthenticator] into `@ApiClient Set<Authenticator>`.
 *
 * **Its own client** ([AuthClient]) for login / refresh / logout, with neither of those installed (no circular
 * refresh), on the same base URL ([NetworkConfig]) and the same `Json` the network module provides once.
 *
 * One `@Singleton` [DefaultAuthManager] serves two roles: [AuthManager] (public) and [RefreshCoordinator]
 * (internal — only the authenticator drives refreshes).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthModule {

    @Binds
    abstract fun bindAuthManager(impl: DefaultAuthManager): AuthManager

    @Binds
    abstract fun bindRefreshCoordinator(impl: DefaultAuthManager): RefreshCoordinator

    @Binds
    abstract fun bindSecureTokenStore(impl: KeystoreSecureTokenStore): SecureTokenStore

    @Binds
    abstract fun bindProfileStore(impl: SharedPrefsProfileStore): ProfileStore

    @Binds
    abstract fun bindAuthService(impl: RetrofitAuthService): AuthService

    @Binds
    abstract fun bindUserActivityTracker(impl: DefaultUserActivityTracker): UserActivityTracker

    @Binds
    @IntoSet
    abstract fun contributeAuthInterceptor(impl: AuthInterceptor): ApiInterceptor

    @Binds
    @IntoSet
    @ApiClient
    abstract fun contributeTokenAuthenticator(impl: TokenAuthenticator): Authenticator

    companion object {

        private const val TIMEOUT_SECONDS = 15L

        @Provides
        @Singleton
        fun provideProactiveRefreshConfig(): ProactiveRefreshConfig = ProactiveRefreshConfig()

        @Provides
        @Singleton
        @AuthScope
        fun provideAuthScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @Singleton
        @AuthClient
        fun provideAuthOkHttpClient(httpLogSettings: HttpLogSettingsRepository): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .apply {
                    if (BuildConfig.DEBUG) addInterceptor(reporterHttpLoggingInterceptor("OkHttp-Auth", httpLogSettings))
                }
                .build()

        @Provides
        @Singleton
        @AuthRetrofit
        fun provideAuthRetrofit(@AuthClient client: OkHttpClient, config: NetworkConfig, json: Json): Retrofit =
            Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

        @Provides
        @Singleton
        fun provideAuthApi(@AuthRetrofit retrofit: Retrofit): AuthApi = retrofit.create()
    }
}
