package {{ROOT_PACKAGE}}.libcommon.lifecycle

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAppForegroundWatchTest {

    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("not used")
    }

    @Test
    fun `starts in the background and follows process start and stop`() {
        val watch = DefaultAppForegroundWatch()
        assertFalse(watch.isForeground.value)

        watch.onStart(owner)
        assertTrue(watch.isForeground.value)

        watch.onStop(owner)
        assertFalse(watch.isForeground.value)
    }
}
