package {{ROOT_PACKAGE}}.auth.token

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import {{ROOT_PACKAGE}}.libcommon.coroutines.DefaultDispatcherProvider

/**
 * The one part of the session that needs a device: the real cipher and Android Keystore round trip (TEST-05).
 * Not part of the default CI gate; run with `./gradlew :auth:connectedDebugAndroidTest` on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecureTokenStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = KeystoreSecureTokenStore(context, DefaultDispatcherProvider())

    @Before
    fun setUp() = runBlocking { store.clear() }

    @After
    fun tearDown() = runBlocking { store.clear() }

    @Test
    fun aSavedTokenReadsBackAsTheOriginal() = runBlocking {
        store.save("alice", "refresh-secret")

        assertEquals("refresh-secret", store.readRefreshToken("alice"))
        assertEquals("alice", store.currentUserId())
    }

    @Test
    fun theStoredValueIsCiphertextNotThePlaintext() = runBlocking {
        store.save("alice", "refresh-secret")

        val raw = context.getSharedPreferences(KeystoreSecureTokenStore.PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .getString("rt_alice", null)

        assertFalse(raw.isNullOrEmpty())
        assertFalse(raw!!.contains("refresh-secret"))
    }

    @Test
    fun aTamperedCiphertextDegradesToNullWithoutCrashing() = runBlocking {
        store.save("alice", "refresh-secret")
        context.getSharedPreferences(KeystoreSecureTokenStore.PREFS_FILE_NAME, Context.MODE_PRIVATE)
            .edit().putString("rt_alice", "bm90LWNpcGhlcnRleHQtYXQtYWxs").commit()

        assertNull(store.readRefreshToken("alice"))
    }

    @Test
    fun clearRemovesEverything() = runBlocking {
        store.save("alice", "refresh-secret")
        store.clear()

        assertNull(store.readRefreshToken("alice"))
        assertNull(store.currentUserId())
    }
}
