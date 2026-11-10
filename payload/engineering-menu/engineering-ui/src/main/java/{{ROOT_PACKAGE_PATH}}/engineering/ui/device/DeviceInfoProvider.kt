package {{ROOT_PACKAGE}}.engineering.ui.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

/** What the menu's "Device" section reports. Plain data, so the section stays `Context`-free and testable. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val packageName: String,
    val appVersionName: String,
    val appVersionCode: String,
)

/** Reads the device and application identity off the OS. A seam so the section is tested against fixed data. */
interface DeviceInfoProvider {

    fun read(): DeviceInfo
}

/**
 * The shipped [DeviceInfoProvider].
 *
 * The app version comes from [PackageManager], **not** from `:app`'s `BuildConfig`: a library module cannot see the
 * application's generated `BuildConfig`, and the installed package is the more honest answer anyway (it is what is
 * actually running, suffixes included).
 */
@Singleton
internal class OsDeviceInfoProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceInfoProvider {

    private val logTag = logTag()

    override fun read(): DeviceInfo {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.onFailure {
            Reporter.error("could not read package info", exception = it, where = logTag)
        }.getOrNull()

        val versionCode = packageInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                it.versionCode.toString()
            }
        }

        return DeviceInfo(
            manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { UNKNOWN },
            model = Build.MODEL.orEmpty().ifBlank { UNKNOWN },
            androidVersion = Build.VERSION.RELEASE.orEmpty().ifBlank { UNKNOWN },
            apiLevel = Build.VERSION.SDK_INT,
            packageName = context.packageName,
            appVersionName = packageInfo?.versionName ?: UNKNOWN,
            appVersionCode = versionCode ?: UNKNOWN,
        )
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }
}
