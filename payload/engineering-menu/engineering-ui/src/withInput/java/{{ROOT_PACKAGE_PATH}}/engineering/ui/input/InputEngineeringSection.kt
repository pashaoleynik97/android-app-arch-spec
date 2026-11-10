package {{ROOT_PACKAGE}}.engineering.ui.input

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringControl
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringOption
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringRow
import {{ROOT_PACKAGE}}.engineering.ui.api.EngineeringSection
import {{ROOT_PACKAGE}}.input.InputController
import {{ROOT_PACKAGE}}.input.InputPreference
import {{ROOT_PACKAGE}}.input.InputPreferenceCodec
import {{ROOT_PACKAGE}}.input.api.Detection
import {{ROOT_PACKAGE}}.input.api.InputDriverId
import {{ROOT_PACKAGE}}.input.api.InputDriverState
import {{ROOT_PACKAGE}}.input.api.InputEvent
import {{ROOT_PACKAGE}}.input.settings.InputSettingsRepository
import javax.inject.Inject

/**
 * The hardware-input section of the engineering menu — compiled only when the hardware-input component is
 * installed (see `build.gradle.kts`). Everything an engineer needs on a device whose input "does nothing":
 *
 * - **Input driver** picker: Auto / a forced driver / Disabled — the manual override for a device where
 *   auto-detection fails. Applied immediately; persisted by the settings repository.
 * - **Active driver** with its live state (and the failure, if the claim failed).
 * - one **Detection** row per registered provider, with its evidence — "no driver" becomes diagnosable without
 *   logcat.
 * - **Last reading** received while the menu is visible: kind, source, extras and a masked payload (a reading can
 *   be a credential; the payload is never shown in full or logged).
 */
class InputEngineeringSection @Inject constructor(
    private val controller: InputController,
    private val settings: InputSettingsRepository,
) : EngineeringSection {

    override val key: String = "hardware-input"
    override val title: String = "Hardware input"
    override val order: Int = 20

    override val rows: Flow<List<EngineeringRow>> = combine(
        settings.preference,
        controller.activeDriverId,
        controller.state,
        controller.events.map<InputEvent, InputEvent?> { it }.onStart { emit(null) },
    ) { preference, activeId, state, last ->
        buildList {
            add(driverPickerRow(preference))
            add(activeDriverRow(activeId, state))
            controller.detectAll().forEach { (provider, detection) ->
                add(
                    EngineeringRow(
                        key = "detect-${provider.id.value}",
                        title = "Detection: ${provider.displayName}",
                        description = when (detection) {
                            is Detection.Supported -> "Supported — ${detection.evidence} (priority ${provider.priority})"
                            Detection.Unsupported -> "Not detected on this device (priority ${provider.priority})"
                        },
                    ),
                )
            }
            add(lastReadingRow(last))
        }
    }

    private fun driverPickerRow(preference: InputPreference?): EngineeringRow {
        val options = buildList {
            add(EngineeringOption(InputPreferenceCodec.encode(InputPreference.Auto), "Auto", "Highest-priority detected driver"))
            controller.detectAll().forEach { (provider, detection) ->
                add(
                    EngineeringOption(
                        key = InputPreferenceCodec.encode(InputPreference.Forced(provider.id)),
                        label = provider.displayName,
                        description = if (detection is Detection.Supported) "Detected" else "Not detected — force anyway",
                    ),
                )
            }
            add(EngineeringOption(InputPreferenceCodec.encode(InputPreference.Disabled), "Disabled", "No hardware input"))
        }
        val selectedKey = preference?.let(InputPreferenceCodec::encode)
        return EngineeringRow(
            key = "driver",
            title = "Input driver",
            description = options.firstOrNull { it.key == selectedKey }?.label ?: "Loading…",
            control = EngineeringControl.Picker(options = options, selectedKey = selectedKey) { key ->
                InputPreferenceCodec.decode(key)?.let(settings::setPreference)
            },
            // Nothing to change until the stored preference is known.
            enabled = preference != null,
        )
    }

    private fun activeDriverRow(activeId: InputDriverId?, state: InputDriverState) = EngineeringRow(
        key = "active",
        title = "Active driver",
        description = when {
            activeId == null -> "None"
            state is InputDriverState.Error -> "${activeId.value} — Error: ${state.cause.message ?: state.cause::class.java.simpleName}"
            else -> "${activeId.value} — ${state::class.java.simpleName}"
        },
    )

    private fun lastReadingRow(event: InputEvent?) = EngineeringRow(
        key = "last-reading",
        title = "Last reading",
        description = event?.let {
            "${maskPayload(it.data)} · kind=${it.kind ?: "?"} · source=${it.source.value}" +
                if (it.extras.isEmpty()) "" else " · ${it.extras}"
        } ?: "Nothing received while this menu is open — trigger the hardware now.",
    )

    companion object {
        private const val VISIBLE_HEAD = 4
        private const val VISIBLE_TAIL = 3

        /** Enough to recognise a reading, not enough to reuse it: `4006…931 (13 chars)`. */
        fun maskPayload(data: String): String =
            if (data.length <= VISIBLE_HEAD + VISIBLE_TAIL) {
                "${"•".repeat(data.length)} (${data.length} chars)"
            } else {
                "${data.take(VISIBLE_HEAD)}…${data.takeLast(VISIBLE_TAIL)} (${data.length} chars)"
            }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class InputEngineeringModule {

    @Binds
    @IntoSet
    abstract fun bindInputSection(impl: InputEngineeringSection): EngineeringSection
}
