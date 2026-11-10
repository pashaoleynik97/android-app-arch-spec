package {{ROOT_PACKAGE}}.input

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import {{ROOT_PACKAGE}}.input.api.Detection
import {{ROOT_PACKAGE}}.input.api.InputDriver
import {{ROOT_PACKAGE}}.input.api.InputDriverId
import {{ROOT_PACKAGE}}.input.api.InputDriverProvider
import {{ROOT_PACKAGE}}.input.api.InputDriverState
import {{ROOT_PACKAGE}}.input.api.InputEvent
import {{ROOT_PACKAGE}}.input.di.InputScope
import {{ROOT_PACKAGE}}.input.settings.InputSettingsRepository
import {{ROOT_PACKAGE}}.libandroid.host.HostActivityObserver
import {{ROOT_PACKAGE}}.libcommon.logging.Reporter
import {{ROOT_PACKAGE}}.libcommon.logging.logTag
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single app-scoped hardware-input controller: it owns the active [InputDriver], follows the **Activity**
 * lifecycle, and is the only input type a screen or ViewModel ever sees.
 *
 * - **Preference → resolver → driver.** The persisted preference is resolved to a driver; a new resolution
 *   disposes the previous driver (`runningFold`), so a swap on a settings screen never leaks a claim.
 * - **Activity lifecycle, not process lifecycle.** Registered on `MainActivity` through the host's
 *   [HostActivityObserver] multibinding (the component's DI module binds it). `ProcessLifecycleOwner` delays
 *   `ON_STOP`; an exclusive hardware claim must be released promptly on pause so other apps can use the device.
 * - **Activation follows (driver, resumed)** through `collectLatest`, which may cancel an in-flight `activate()` —
 *   hence the driver contract's idempotency requirement.
 * - **Events are shared, replay-free and logged once** (see [events]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class InputController @Inject constructor(
    private val resolver: InputDriverResolver,
    settings: InputSettingsRepository,
    @param:InputScope private val appScope: CoroutineScope,
) : HostActivityObserver {

    private val logTag = logTag()

    private val resumed = MutableStateFlow(false)

    /**
     * The active driver. `filterNotNull` skips the repository's "not read yet" value, so a cold start never claims
     * the hardware on a guessed `Auto` and releases it a moment later.
     */
    private val resolution: StateFlow<DriverResolution> = settings.preference
        .filterNotNull()
        .map { preference -> resolver.resolve(preference) }
        .runningFold(DriverResolution(driver = null, isResolved = false)) { previous, next ->
            previous.driver?.dispose()
            Reporter.debug("input driver -> ${next?.id?.value ?: "none"}", where = logTag)
            DriverResolution(driver = next, isResolved = true)
        }
        .stateIn(appScope, SharingStarted.Eagerly, DriverResolution(driver = null, isResolved = false))

    private val driver: StateFlow<InputDriver?> = resolution
        .map { it.driver }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    /**
     * Every reading, from whichever driver is active.
     *
     * **This is the one place a reading is logged**, and the reason the flow is shared rather than cold: a plain
     * `flatMapLatest` chain would be re-collected per subscriber and log the same reading once per listening screen.
     * Sharing it eagerly also logs readings when *nothing* collects — exactly the case someone is diagnosing when
     * "the scanner does nothing".
     *
     * `replay = 0` is load-bearing: a replayed reading would be redelivered to a newly opened screen and act twice.
     * The payload is **redacted** in the log line (length only): a reading can be a credential (an access badge),
     * and release logs leave the device through external reporters. Show payloads on a diagnostics screen instead.
     */
    val events: Flow<InputEvent> = driver
        .flatMapLatest { it?.events ?: emptyFlow() }
        .onEach { event ->
            Reporter.debug(
                "input: ${event.data.length} chars kind=${event.kind} source=${event.source.value} " +
                    "bytes=${event.rawBytes?.size ?: 0} extras=${event.extras.keys}",
                where = logTag,
            )
        }
        .shareIn(appScope, SharingStarted.Eagerly, replay = 0)

    /** The active driver's state, or [InputDriverState.Idle] when there is no driver. */
    val state: StateFlow<InputDriverState> = driver
        .flatMapLatest { it?.state ?: flowOf(InputDriverState.Idle) }
        .distinctUntilChanged()
        .onEach { state ->
            if (state is InputDriverState.Error) {
                Reporter.error("input driver state -> Error", exception = state.cause, where = logTag)
            } else {
                Reporter.debug("input driver state -> $state", where = logTag)
            }
        }
        .stateIn(appScope, SharingStarted.Eagerly, InputDriverState.Idle)

    val activeDriverId: StateFlow<InputDriverId?> = driver
        .map { it?.id }
        .stateIn(appScope, SharingStarted.Eagerly, null)

    /**
     * `true` when the hardware cannot produce a reading: no driver, or the driver failed. `false` until the stored
     * preference has been read, because "not known yet" is not "unavailable" and a screen open at cold start would
     * otherwise flash a warning. [InputDriverState.Idle] does not count: it is also the state in the background.
     */
    val isUnavailable: StateFlow<Boolean> = combine(resolution, state) { resolution, state ->
        resolution.isResolved && (resolution.driver == null || state is InputDriverState.Error)
    }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.Eagerly, false)

    init {
        // Handles lifecycle changes AND a driver swap while resumed (a preference changed on a settings screen).
        appScope.launch {
            combine(driver, resumed, ::Pair).collectLatest { (driver, isResumed) ->
                driver ?: return@collectLatest
                if (isResumed) driver.activate() else driver.deactivate()
            }
        }
    }

    /** Every registered provider with its detection verdict — a settings screen's diagnostic read. */
    fun detectAll(): List<Pair<InputDriverProvider, Detection>> = resolver.detectAll()

    override fun onResume(owner: LifecycleOwner) {
        resumed.value = true
    }

    override fun onPause(owner: LifecycleOwner) {
        resumed.value = false
    }
}

/** One value, so a resolved "no driver" can never be observed apart from the fact that it is resolved. */
private data class DriverResolution(val driver: InputDriver?, val isResolved: Boolean)
