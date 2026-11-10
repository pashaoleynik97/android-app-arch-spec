# Recommended: hardware-input — pluggable vendor input drivers (INPUT)

Tier: **Recommended** (component `hardware-input`). SHOULD / MAY guidance with a *why* per rule.

The component adds three modules: `:input-api` (`{{ROOT_PACKAGE}}.input.api` — the contract), `:input`
(`{{ROOT_PACKAGE}}.input` — `InputController`, `InputDriverResolver`, the settings store and repository,
`CollectInputWhileResumed`), and `:input-debug` (`{{ROOT_PACKAGE}}.input.debug` — an adb-broadcast driver, wired with
`debugImplementation` only). Vendor drivers are added later, one module each (`input-api/README.md`).

## When to use

- The app reads from **device hardware that delivers discrete readings** — a built-in barcode or RFID reader, an
  NFC/smart-card reader, a payment or badge terminal, a hardware keypad — and that hardware differs between device
  models or vendors.
- More than one screen consumes those readings, or the hardware must be claimed and released with the app's
  foreground state.
- You need to develop and test the flows on an emulator without the hardware.

## When not to

- The camera is the only input (in-app scanning with ML Kit / CameraX): that is a screen-owned capture
  (`patterns/capture-for-result.md`), not a background driver.
- One fixed device model with one SDK and one screen using it: a single injected class is enough — adopt this when
  the second vendor or the second consumer arrives.
- Keyboard-wedge input (the hardware types into the focused field) is enough for the product: no driver needed, but
  expect focus bugs; a driver is the robust option.

---

## 1. Module shape

**INPUT-01** — The stack SHOULD be split into a **leaf contract** module (`:input-api`: no Hilt, no Compose, no
vendor), a **core** module (`:input`: controller, resolver, settings), **one module per vendor** (`:input-<vendor>`,
depending on `:input-api` only), and a **debug driver** module wired with `debugImplementation`.
*Why:* a vendor SDK and its manifest needs stay inside one module that can be added or dropped with one dependency
line; the release build proves the debug driver is absent (MOD-17).

**INPUT-02** — The core module SHOULD declare `@Multibinds Set<InputDriverProvider>` and every driver module SHOULD
contribute its provider with `@Binds @IntoSet`.
*Why:* a build with no vendor module (a release build of an app that has only the debug driver) must still compile
(DI-13); adding a vendor touches no existing code.

## 2. The driver contract

**INPUT-03** — `InputDriver` SHOULD model **intent** — `activate()` ("the app is in front and wants input") and
`deactivate()` — not a vendor's steps, and both SHOULD be **idempotent and safe in any order**; `dispose()` releases
everything.
*Why:* vendors initialise very differently; an interface shaped around one fits none of the others. The controller
drives activation from `collectLatest`, which may cancel an `activate()` half-way and call `deactivate()` next.

**INPUT-04** — A driver's `events` SHOULD be hot and **replay-free** (`MutableSharedFlow(extraBufferCapacity = 8)` +
`tryEmit`, logging a dropped event); a failed claim SHOULD release what it took and set `InputDriverState.Error`.
*Why:* a replayed reading would be delivered to a newly opened screen and act twice; a half-claimed device blocks
other apps.

**INPUT-05** — Vendor vocabulary (code ids, symbology numbers) SHOULD be mapped to the app's normalized
`InputEvent.kind` strings **inside the driver**, with everything raw kept in `extras` for diagnostics only; no feature
branches on `extras`. `InputEvent` equality SHOULD exclude `rawBytes`.
*Why:* vendor terms never leak past their module; diagnostics stay possible where the mapping is wrong; arrays compare
by identity, so a generated `equals` would call two identical readings different.

**INPUT-06** — `detect()` SHOULD be **capability-based** (the vendor's service package is installed:
`context.isPackageInstalled(pkg)`), with the vendor package declared in a `<queries>` block of the **vendor
module's** manifest; `Build.MANUFACTURER` belongs only in the evidence string.
*Why:* rebranded devices and unusual OEM builds break manufacturer matching; without `<queries>` the package check is
always `false` on Android 11+ and auto-detection fails silently.

## 3. Resolution and settings

**INPUT-07** — Resolution SHOULD follow a persisted preference: `Disabled` → no driver; `Forced(id)` → that provider
**without** detection (an unknown id falls back to `Auto` with an error report); `Auto` → the highest-priority provider
whose detection is `Supported`. Real hardware uses a positive priority; the debug driver −100.
*Why:* `Forced` is the manual override for devices where detection fails — the reason a settings screen exists; the
debug driver must win only where no hardware is present (an emulator).

**INPUT-08** — The preference SHOULD be stored as one string (`auto` | `disabled` | `forced:<id>`) in plain
preferences, not wiped on logout, and an unreadable value read as `Auto` with an error report. Its live flow SHOULD
start as `null` ("not loaded") and the controller SHOULD ignore `null`; driver ids are never renamed once shipped.
*Why:* the setting belongs to the device, not the signed-in user; a guessed initial `Auto` claims and releases the
hardware at every cold start when the stored value is `Disabled`.

## 4. The controller

**INPUT-09** — One `@Singleton` controller SHOULD own the active driver, dispose the previous driver on every
re-resolution (`runningFold`), and follow the **Activity** lifecycle — registered through the host's
`Set<HostActivityObserver>` — activating while resumed and deactivating on pause (`combine(driver, resumed)` +
`collectLatest`) on a `SupervisorJob + Main.immediate` scope.
*Why:* `ProcessLifecycleOwner` delays `ON_STOP`; an exclusive claim must be released promptly so other apps can use the
hardware; main-immediate keeps the release in step with the pause callback.

**INPUT-10** — The controller's `events` SHOULD be **shared once** (`shareIn(appScope, Eagerly, replay = 0)`) and
logged **once, there**, with the payload redacted (length, kind, source, extras keys).
*Why:* a cold chain is re-collected per subscriber and logs each reading per listening screen; eager sharing logs
readings even when no screen listens — the situation being diagnosed when "the reader does nothing"; a reading can be
a credential (a badge), and release logs leave the device (KERNEL-05).

**INPUT-11** — "Unavailable" SHOULD be derived (`resolved && (no driver || Error)`) and stay `false` until the
preference has been read.
*Why:* "not known yet" is not "unavailable"; a screen open at cold start would flash a warning.

## 5. Screens

**INPUT-12** — Screens SHOULD consume readings only while their destination is RESUMED
(`CollectInputWhileResumed(viewModel.inputEvents, viewModel::onInput)`), handle them **idempotently**, and give
visible feedback for a reading that matches nothing.
*Why:* only the top destination is RESUMED, so a screen in the back stack cannot react to input meant for the front
one (UI-14); double reads are normal hardware behaviour; silence reads as "the device is broken".

## 6. The debug driver

**INPUT-13** — The debug driver SHOULD listen for an app-unique broadcast action built from the **installed package
name** (`<applicationId>.input.DEBUG_EVENT`, extras `data` and `kind`), register its receiver only while active,
with an explicit `RECEIVER_EXPORTED` (required for runtime receivers targeting API 33+), and report
`Detection.Supported` at priority −100.
*Why:* `adb shell am broadcast` comes from the shell uid, so the receiver must be exported — acceptable only because
the module never ships in release; a package-derived action keeps variants with suffixes apart.

## 7. Testing

**INPUT-14** — The resolver, codec, repository and controller SHOULD be JVM-tested with fakes (a driver that counts
`activate` / `deactivate` / `dispose` and emits on demand, a provider with a scripted verdict, an in-memory store):
no driver before the preference is read; resume activates / pause releases; a swap disposes the old driver and
activates the new one; two collectors see one reading; a late collector sees none. Broadcast receivers and vendor
SDKs are verified on a device; their pure parts (intent-extra parsing, kind-mapping tables) are JVM-tested.
*Why:* these are the rules that decide whether the device has working input at all.

## Adapting this to your app

- **Add vendors** with the six steps in `input-api/README.md`; nothing in `:input` changes.
- **Define your kinds** (`"ean13"`, `"qr"`, `"nfc-a"`, …) once in your app (a constants object in the consuming
  domain) and map every vendor onto them in its driver.
- **Diagnostics:** with the `engineering-menu` component installed, a "Hardware input" section (driver picker,
  detection evidence, live state, last reading) appears automatically.
- **Example-only:** the debug action/extras names and the `debug` driver id; change them together with the docs and
  any scripts that broadcast.
