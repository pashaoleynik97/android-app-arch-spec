package {{APP_PACKAGE}}.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import {{ROOT_PACKAGE}}.media.MediaStoreConfig
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * The host's configuration of `:media` (the host owns every config an infra module needs).
 *
 * The TTL is how long an orphaned capture may survive on the device. Pick it from the longest flow a capture
 * legitimately waits through (capture → publish → the work it documents is finished) plus margin; shorter is
 * safer for data on shared devices, longer survives an overnight pause.
 */
@Module
@InstallIn(SingletonComponent::class)
object MediaConfigModule {

    @Provides
    @Singleton
    fun provideMediaStoreConfig(): MediaStoreConfig =
        MediaStoreConfig(staleAfterMillis = TimeUnit.HOURS.toMillis(STALE_AFTER_HOURS))

    private const val STALE_AFTER_HOURS = 12L
}
