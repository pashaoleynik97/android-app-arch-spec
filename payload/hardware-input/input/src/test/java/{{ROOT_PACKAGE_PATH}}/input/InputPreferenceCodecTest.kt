package {{ROOT_PACKAGE}}.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import {{ROOT_PACKAGE}}.input.api.InputDriverId

class InputPreferenceCodecTest {

    @Test
    fun `every preference round-trips`() {
        listOf(
            InputPreference.Auto,
            InputPreference.Disabled,
            InputPreference.Forced(InputDriverId("vendor-x")),
        ).forEach { preference ->
            assertEquals(preference, InputPreferenceCodec.decode(InputPreferenceCodec.encode(preference)))
        }
    }

    @Test
    fun `nothing stored reads as auto`() {
        assertEquals(InputPreference.Auto, InputPreferenceCodec.decode(null))
    }

    @Test
    fun `an unrecognized or truncated value is reported as unreadable`() {
        assertNull(InputPreferenceCodec.decode("something-from-a-newer-build"))
        assertNull(InputPreferenceCodec.decode("forced:"))
    }
}
