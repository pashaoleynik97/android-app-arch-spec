package {{ROOT_PACKAGE}}.engineering.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import {{ROOT_PACKAGE}}.engineering.ui.device.DeviceInfo
import {{ROOT_PACKAGE}}.engineering.ui.device.DeviceInfoProvider
import {{ROOT_PACKAGE}}.engineering.ui.device.DeviceInfoSection

class DeviceInfoSectionTest {

    private val provider = object : DeviceInfoProvider {
        override fun read() = DeviceInfo(
            manufacturer = "Example",
            model = "Phone 7",
            androidVersion = "14",
            apiLevel = 34,
            packageName = "com.example.app.debug",
            appVersionName = "1.4.2-test",
            appVersionCode = "1040020",
        )
    }

    @Test
    fun `renders the device and app identity as plain rows`() = runTest {
        val rows = DeviceInfoSection(provider).rows.first()

        assertEquals(
            listOf(
                "Device" to "Example Phone 7",
                "Android" to "14 (API 34)",
                "App version" to "1.4.2-test (1040020)",
                "Package" to "com.example.app.debug",
            ),
            rows.map { it.title to it.description },
        )
    }

    @Test
    fun `it is the first section`() {
        assertEquals(0, DeviceInfoSection(provider).order)
    }
}
