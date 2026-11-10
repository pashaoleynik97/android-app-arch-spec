package {{ROOT_PACKAGE}}.media.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import {{ROOT_PACKAGE}}.libcommon.coroutines.DispatcherProvider
import {{ROOT_PACKAGE}}.media.LocalMediaStore
import {{ROOT_PACKAGE}}.media.MediaClock
import {{ROOT_PACKAGE}}.media.MediaDirectory
import {{ROOT_PACKAGE}}.media.internal.InternalMediaStore
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** The dispatcher every file operation in `:media` is confined to. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaIoDispatcher

/**
 * Application-lifetime scope for deletions and the startup sweep — work that must outlive the screen or the
 * ViewModel that asked for it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaScope

/**
 * DI for `:media`.
 *
 * Deliberately **unbound** here: [{{ROOT_PACKAGE}}.media.MediaStoreConfig] — the host provides it (the
 * component ships `:app`'s `MediaConfigModule`), because the TTL is a product decision and a test build may need to
 * shrink it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {

    @Binds
    internal abstract fun bindLocalMediaStore(impl: InternalMediaStore): LocalMediaStore

    companion object {

        @Provides
        @Singleton
        @MediaIoDispatcher
        fun provideMediaIoDispatcher(dispatchers: DispatcherProvider): CoroutineDispatcher = dispatchers.io

        @Provides
        @Singleton
        @MediaScope
        fun provideMediaScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(SupervisorJob() + dispatchers.io)

        /** The wall clock, on purpose — see [MediaClock]. */
        @Provides
        @Singleton
        fun provideMediaClock(): MediaClock = MediaClock { System.currentTimeMillis() }

        /** `filesDir/media`. The one place a `Context` is read in this module, which keeps the store a JVM class. */
        @Provides
        @Singleton
        fun provideMediaDirectory(@ApplicationContext context: Context): MediaDirectory =
            MediaDirectory { File(context.filesDir, MEDIA_DIR_NAME) }

        /** Also named in the backup-exclusion rule the install doc asks the host to add. Keep them in sync. */
        const val MEDIA_DIR_NAME = "media"
    }
}
