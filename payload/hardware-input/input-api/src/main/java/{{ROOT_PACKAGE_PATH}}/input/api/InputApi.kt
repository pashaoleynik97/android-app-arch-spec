package {{ROOT_PACKAGE}}.input.api

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stable identifier of a driver implementation — `"debug"`, `"<vendor>"`, … It is **persisted** in the input
 * settings (a forced choice), so a value may never be renamed once shipped.
 */
@JvmInline
value class InputDriverId(val value: String)

/**
 * One reading from a hardware input device (a barcode or RFID read, an NFC tag, a keypad entry), vendor-neutral.
 *
 * @param data the decoded payload — the contract every screen branches on.
 * @param rawBytes the undecoded bytes when the vendor provides them (binary payloads, charset problems).
 * @param kind the normalized kind of reading (`"ean13"`, `"qr"`, `"nfc-a"`, …) or `null` when the vendor did not
 *   say. Each driver maps its vendor's own codes onto these strings **inside the driver**, so vendor vocabulary
 *   never leaves its module. Keep the set your app recognizes in one place (a constants object in the app).
 * @param source which driver produced it.
 * @param timestamp wall-clock millis of the reading (diagnostics and de-duplication windows).
 * @param extras a **diagnostics-only** bag of everything else the vendor delivered, stringified (raw code ids,
 *   charset, firmware version). It exists so an engineering screen can show the truth even where this build's
 *   mapping is wrong. **No feature screen may branch on it** — [data] and [kind] are the contract.
 */
class InputEvent(
    val data: String,
    val rawBytes: ByteArray?,
    val kind: String?,
    val source: InputDriverId,
    val timestamp: Long,
    val extras: Map<String, String> = emptyMap(),
) {

    /**
     * Equality **excludes [rawBytes]** on purpose: arrays compare by identity, so a generated `equals` would answer
     * "different" for two events carrying the same reading. Two events are the same when they carry the same
     * payload from the same source at the same moment.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InputEvent) return false
        return data == other.data &&
            kind == other.kind &&
            source == other.source &&
            timestamp == other.timestamp &&
            extras == other.extras
    }

    override fun hashCode(): Int {
        var result = data.hashCode()
        result = 31 * result + (kind?.hashCode() ?: 0)
        result = 31 * result + source.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + extras.hashCode()
        return result
    }

    override fun toString(): String =
        "InputEvent(data='$data', kind=$kind, source=${source.value}, timestamp=$timestamp, " +
            "bytes=${rawBytes?.size ?: 0}, extras=$extras)"
}

/** Where a driver currently stands. Shown verbatim on a diagnostics screen. */
sealed interface InputDriverState {
    /** Not claimed — the app is in the background, or the driver was just created. */
    data object Idle : InputDriverState

    /** Claiming the hardware. */
    data object Initializing : InputDriverState

    /** Claimed; the hardware will deliver events. */
    data object Ready : InputDriverState

    /** The claim failed. [cause] is reported through `Reporter` by the controller. */
    data class Error(val cause: Throwable) : InputDriverState
}

/**
 * One device-input implementation.
 *
 * The interface models **intent** — "the app is in front and wants input" ([activate]) versus "it doesn't"
 * ([deactivate]) — rather than any vendor's steps. Vendors initialise very differently (claim/release per
 * foreground session, profile association, SDK binding), and an interface shaped around one of them fits none of
 * the others.
 *
 * [activate] and [deactivate] MUST be **idempotent and safe in any order**: the controller drives them from a
 * `collectLatest`, which may cancel an `activate` half-way and call `deactivate` next.
 */
interface InputDriver {

    val id: InputDriverId

    /** Hot and **replay-free**. A replayed reading would be delivered to a newly opened screen and act twice. */
    val events: Flow<InputEvent>

    val state: StateFlow<InputDriverState>

    /** The app is in the foreground and wants input. Idempotent. */
    suspend fun activate()

    /** The app left the foreground; give the hardware back to other apps. Idempotent. */
    suspend fun deactivate()

    /** The driver is being replaced or shut down. Releases everything, including an active claim. */
    fun dispose()
}

/** Whether a provider's hardware is present on this device. */
sealed interface Detection {

    /** [evidence] is shown verbatim on a diagnostics screen — it makes "nothing detected" diagnosable. */
    data class Supported(val evidence: String) : Detection

    data object Unsupported : Detection
}

/**
 * Creates an [InputDriver] for one vendor and knows whether that vendor's hardware is present. Contributed with
 * `@Binds @IntoSet` from the vendor's own module, so adding a vendor adds a module and edits no existing code.
 *
 * Neither method takes a `Context`: a provider is Hilt-constructed and injects `@ApplicationContext` itself. That
 * keeps the resolver free of Android, so its rules are unit-tested against fake providers.
 */
interface InputDriverProvider {

    val id: InputDriverId

    /** Shown in the settings picker. Not localized — these are vendor names. */
    val displayName: String

    /** Tie-breaker when several providers report [Detection.Supported]; highest wins. A debug driver uses < 0. */
    val priority: Int

    /** Cheap, synchronous, side-effect free. */
    fun detect(): Detection

    /** A fresh driver on every call. */
    fun create(): InputDriver
}

/**
 * Capability-based detection: whether the vendor's own service package is installed is far more reliable than
 * matching `Build.MANUFACTURER`, which rebranded devices and unusual OEM builds break (the manufacturer belongs in
 * the evidence string only). **Requires the vendor package in a `<queries>` block of the vendor module's
 * manifest** — without it this returns `false` on Android 11+ and auto-detection fails silently.
 */
fun Context.isPackageInstalled(packageName: String): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
