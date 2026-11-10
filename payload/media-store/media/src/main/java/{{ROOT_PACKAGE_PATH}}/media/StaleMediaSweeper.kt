package {{ROOT_PACKAGE}}.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import {{ROOT_PACKAGE}}.media.di.MediaScope
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes captures an earlier run of the app left behind.
 *
 * Ordinary cleanup happens where the files are used — a published capture is kept or deleted, a discarded one is
 * deleted. This exists for the runs that reached neither: a crash, a process the system killed while the user was in
 * the camera, a user who put the device down mid-flow. Without it those files would sit in app storage forever.
 *
 * It runs **once per process, at startup**, on the media scope: [MediaStartupInitializer] (an `androidx.startup`
 * initializer merged into the app manifest by this module) calls [start] — so the host's `App` class needs no edit
 * and the sweep also runs when the process is started without UI. One pass is enough: a file created after it ran
 * cannot be older than the TTL before the next process start.
 */
@Singleton
class StaleMediaSweeper @Inject constructor(
    private val store: LocalMediaStore,
    @param:MediaScope private val scope: CoroutineScope,
) {

    private val logTag = logTag()
    private val started = AtomicBoolean(false)

    /** Starts the sweep; every call after the first is a no-op. Never throws — a failed sweep is only logged. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatching { store.sweepStale() }
                .onFailure { Reporter.error("stale media sweep failed", exception = it, where = logTag) }
        }
    }
}
