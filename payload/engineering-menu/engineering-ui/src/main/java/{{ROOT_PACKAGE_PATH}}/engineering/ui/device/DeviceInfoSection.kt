package {{ROOT_PACKAGE}}.engineering.ui.device

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRow
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringSection
import javax.inject.Inject

/**
 * The menu's built-in first section: who and what is running — the first thing a support conversation asks.
 * Read once per collection (nothing in it changes while the menu is open).
 */
class DeviceInfoSection @Inject constructor(
    private val provider: DeviceInfoProvider,
) : EngineeringSection {

    override val key: String = "device"
    override val title: String = "Device"
    override val order: Int = 0

    override val rows: Flow<List<EngineeringRow>> = flow {
        val info = provider.read()
        emit(
            listOf(
                EngineeringRow(key = "device", title = "Device", description = "${info.manufacturer} ${info.model}"),
                EngineeringRow(
                    key = "android",
                    title = "Android",
                    description = "${info.androidVersion} (API ${info.apiLevel})",
                ),
                EngineeringRow(
                    key = "app-version",
                    title = "App version",
                    description = "${info.appVersionName} (${info.appVersionCode})",
                ),
                EngineeringRow(key = "package", title = "Package", description = info.packageName),
            ),
        )
    }
}
