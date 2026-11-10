package {{ROOT_PACKAGE}}.input

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import {{ROOT_PACKAGE}}.input.api.Detection
import {{ROOT_PACKAGE}}.input.api.InputDriver
import {{ROOT_PACKAGE}}.input.api.InputDriverId
import {{ROOT_PACKAGE}}.input.api.InputDriverProvider
import {{ROOT_PACKAGE}}.input.api.InputDriverState
import {{ROOT_PACKAGE}}.input.api.InputEvent
import {{ROOT_PACKAGE}}.input.settings.InputSettingsStore

/** A driver that records what was asked of it, so the controller's lifecycle rules can be asserted. */
class FakeInputDriver(override val id: InputDriverId) : InputDriver {

    var activateCount = 0
        private set
    var deactivateCount = 0
        private set
    var disposeCount = 0
        private set

    private val mEvents = MutableSharedFlow<InputEvent>(extraBufferCapacity = 8)
    override val events = mEvents.asSharedFlow()

    private val mState = MutableStateFlow<InputDriverState>(InputDriverState.Idle)
    override val state = mState.asStateFlow()

    override suspend fun activate() {
        activateCount++
        mState.value = InputDriverState.Ready
    }

    override suspend fun deactivate() {
        deactivateCount++
        mState.value = InputDriverState.Idle
    }

    override fun dispose() {
        disposeCount++
    }

    fun fail(cause: Throwable) {
        mState.value = InputDriverState.Error(cause)
    }

    fun emit(data: String) = mEvents.tryEmit(
        InputEvent(data = data, rawBytes = null, kind = null, source = id, timestamp = 0L),
    )
}

/** A provider with a scripted verdict; each [create] hands back a brand-new driver, as a real one does. */
class FakeInputDriverProvider(
    override val id: InputDriverId,
    override val displayName: String = id.value,
    override val priority: Int = 0,
    private val detection: Detection = Detection.Supported("fake"),
) : InputDriverProvider {

    val created = mutableListOf<FakeInputDriver>()

    override fun detect(): Detection = detection

    override fun create(): InputDriver = FakeInputDriver(id).also { created += it }
}

/** In-memory [InputSettingsStore]. */
class FakeInputSettingsStore(var stored: InputPreference = InputPreference.Auto) : InputSettingsStore {

    var writes = 0
        private set

    override suspend fun read(): InputPreference = stored

    override suspend fun write(preference: InputPreference) {
        writes++
        stored = preference
    }
}
