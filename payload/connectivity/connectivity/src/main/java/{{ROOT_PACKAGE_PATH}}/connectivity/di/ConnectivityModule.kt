package {{ROOT_PACKAGE}}.connectivity.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import {{ROOT_PACKAGE}}.connectivity.NetworkStateWatch
import {{ROOT_PACKAGE}}.connectivity.internal.AndroidConnectivityMonitor
import {{ROOT_PACKAGE}}.connectivity.internal.ConnectivityMonitor
import {{ROOT_PACKAGE}}.connectivity.internal.DefaultNetworkStateWatch
import {{ROOT_PACKAGE}}.connectivity.internal.OkHttpServerHealthProbe
import {{ROOT_PACKAGE}}.connectivity.internal.ServerHealthProbe
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** DI for the network-state watch. The environment ([ConnectivityConfigModule]) is a separate, single place. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConnectivityModule {

    @Binds
    abstract fun bindNetworkStateWatch(impl: DefaultNetworkStateWatch): NetworkStateWatch

    @Binds
    abstract fun bindConnectivityMonitor(impl: AndroidConnectivityMonitor): ConnectivityMonitor

    @Binds
    abstract fun bindServerHealthProbe(impl: OkHttpServerHealthProbe): ServerHealthProbe

    companion object {

        /** Five seconds: a health probe that takes longer is already an answer ("not reachable"). */
        private const val PROBE_TIMEOUT_SECONDS = 5L

        @Provides
        @Singleton
        @ConnectivityClient
        fun provideConnectivityClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        @Provides
        @Singleton
        @ConnectivityScope
        fun provideConnectivityScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
