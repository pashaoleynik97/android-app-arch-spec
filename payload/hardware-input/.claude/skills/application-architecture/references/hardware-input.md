# Hardware input — pluggable vendor drivers (component: hardware-input)

Device hardware that delivers readings (barcode/RFID/NFC readers, keypads, terminals) plugs in as **drivers**; screens
see only `InputController`. Spec: `.arch-spec/spec/specs/recommended/hardware-input.md` (INPUT-*). Adding a vendor:
`input-api/README.md`.

Contents: 1. Modules · 2. Contract · 3. Controller · 4. Screens · 5. Debug driver · 6. Rules · 7. Testing

---

## 1. Modules

| Module | Package | Contains | Wired in `:app` as |
|---|---|---|---|
| `:input-api` | `{{ROOT_PACKAGE}}.input.api` | `InputDriver`, `InputDriverProvider`, `InputEvent`, `InputDriverState`, `Detection`, `InputDriverId`, `Context.isPackageInstalled` | transitively (`:input` api's it) |
| `:input` | `{{ROOT_PACKAGE}}.input` | `InputController`, `InputDriverResolver`, `InputPreference` + codec, settings store/repository, `CollectInputWhileResumed`, `InputModule` | `implementation` |
| `:input-debug` | `{{ROOT_PACKAGE}}.input.debug` | adb-broadcast driver + provider (priority −100) | `debugImplementation` **only** |
| `:input-<vendor>` | `{{ROOT_PACKAGE}}.input.<vendor>` | one vendor driver + provider + its manifest `<queries>` | `implementation` |

## 2. Contract

```kotlin
class InputEvent(data: String, rawBytes: ByteArray?, kind: String?, source: InputDriverId, timestamp: Long,
                 extras: Map<String, String> = emptyMap())       // equals/hashCode exclude rawBytes
interface InputDriver {
    val id: InputDriverId
    val events: Flow<InputEvent>            // hot, replay-free
    val state: StateFlow<InputDriverState>  // Idle | Initializing | Ready | Error(cause)
    suspend fun activate(); suspend fun deactivate()   // idempotent, any order
    fun dispose()
}
interface InputDriverProvider { val id; val displayName; val priority: Int; fun detect(): Detection; fun create(): InputDriver }
```

## 3. Controller

`InputController` (`@Singleton`, a `HostActivityObserver` contributed by `InputModule`, so `MainActivity` registers it):

```kotlin
val events: Flow<InputEvent>                 // shared once, replay = 0, logged once (payload redacted)
val state: StateFlow<InputDriverState>       // active driver's state, Idle when none
val activeDriverId: StateFlow<InputDriverId?>
val isUnavailable: StateFlow<Boolean>        // false until the preference is read
fun detectAll(): List<Pair<InputDriverProvider, Detection>>
```
Preference changes go through `InputSettingsRepository.setPreference(InputPreference.Auto | Forced(id) | Disabled)` —
applied at once, persisted in the background.

## 4. Screens

```kotlin
// ViewModel
val inputEvents: Flow<InputEvent> = inputController.events
fun onInput(event: InputEvent) { /* idempotent; feedback when nothing matches */ }

// Screen
CollectInputWhileResumed(viewModel.inputEvents, viewModel::onInput)
```
A screen that must warn when the hardware is missing collects `inputController.isUnavailable` into its state.

## 5. Debug driver

```
adb shell am broadcast -a {{APPLICATION_ID}}.input.DEBUG_EVENT --es data "4006381333931" --es kind "ean13"
```
On an emulator `Auto` resolves to it (no real driver detects). On a real device it can be forced from the
engineering menu (when installed). The action follows the installed package name, so an `applicationIdSuffix`
changes it.

## 6. Rules

**INPUT-01** — `:input-api` leaf contract, `:input` core, one module per vendor (contract only), debug driver as
`debugImplementation`. *Why:* vendor SDK + manifest needs stay in one droppable module; release proves the debug
driver is absent.
<!-- origin: arch-spec INPUT-01 -->

**INPUT-02** — `@Multibinds Set<InputDriverProvider>` in `:input`; drivers contribute `@Binds @IntoSet`. *Why:* a build
with no driver still compiles; a vendor adds a module, not an edit.
<!-- origin: arch-spec INPUT-02 -->

**INPUT-03** — Drivers model intent (`activate` / `deactivate`), idempotent and order-safe. *Why:* `collectLatest`
may cancel `activate` half-way and call `deactivate` next.
<!-- origin: arch-spec INPUT-03 -->

**INPUT-04** — Driver events are hot, replay-free, `tryEmit` with a logged drop; a failed claim releases and sets
`Error`. *Why:* a replayed reading acts twice on a new screen; half claims block other apps.
<!-- origin: arch-spec INPUT-04 -->

**INPUT-05** — Vendor codes map to app `kind` strings inside the driver; raw values go to `extras` (diagnostics only).
*Why:* vendor vocabulary stays in its module; wrong mappings stay diagnosable.
<!-- origin: arch-spec INPUT-05 -->

**INPUT-06** — Capability-based detection (`isPackageInstalled`) with `<queries>` in the vendor module's manifest.
*Why:* manufacturer matching breaks on rebranded devices; without `<queries>` detection is silently false on 11+.
<!-- origin: arch-spec INPUT-06 -->

**INPUT-07** — `Disabled` → none; `Forced(id)` → that driver without detection (unknown id → `Auto` + error);
`Auto` → highest-priority supported. *Why:* `Forced` is the override for failed detection; debug −100 wins only
without hardware.
<!-- origin: arch-spec INPUT-07 -->

**INPUT-08** — Preference stored as one string, device-scoped (not wiped on logout), unreadable → `Auto` + error; the
live flow starts `null` and the controller ignores it. *Why:* no claim/release flicker at cold start.
<!-- origin: arch-spec INPUT-08 -->

**INPUT-09** — One controller, disposes replaced drivers, follows the Activity lifecycle via `HostActivityObserver`
on a `Main.immediate` scope. *Why:* the claim must be released promptly on pause.
<!-- origin: arch-spec INPUT-09 -->

**INPUT-10** — `events` shared once (`replay = 0`, Eagerly) and logged once, payload redacted. *Why:* one log line per
reading even with several listeners or none; readings can be credentials.
<!-- origin: arch-spec INPUT-10 -->

**INPUT-11** — `isUnavailable` is derived and `false` until the preference is read. *Why:* no warning flash at start.
<!-- origin: arch-spec INPUT-11 -->

**INPUT-12** — Screens collect only while RESUMED (`CollectInputWhileResumed`), handle idempotently, give feedback on
no match. *Why:* back-stack screens must not react; double reads are normal.
<!-- origin: arch-spec INPUT-12 -->

**INPUT-13** — Debug driver: package-derived action, receiver registered only while active with explicit
`RECEIVER_EXPORTED`, priority −100, never in release. *Why:* shell broadcasts need an exported receiver; acceptable
only in a debug-only module.
<!-- origin: arch-spec INPUT-13 -->

## 7. Testing

**INPUT-14** — JVM tests with fakes (`FakeInputDriver` counting activate/deactivate/dispose, `FakeInputDriverProvider`
with a scripted verdict, `FakeInputSettingsStore`): preference not read → no driver; resume/pause; swap disposes and
activates; two collectors one reading; late collector none. Receivers and SDKs are verified on a device; their pure
parts (extra parsing, kind tables) are JVM-tested. *Why:* these rules decide whether input works at all.
<!-- origin: arch-spec INPUT-14 -->

Reference suites: `input/src/test/…` (`InputControllerTest`, `InputDriverResolverTest`, `InputSettingsRepositoryTest`,
`InputPreferenceCodecTest`, `InputTestFakes.kt`), `input-api/src/test/…/InputEventTest.kt`,
`input-debug/src/test/…/DebugInputEventsTest.kt`. When collecting a hot flow from a controller under test, collect on
the controller's own `TestScope` (foreground work): `advanceUntilIdle()` does not wait for `backgroundScope` tasks.
