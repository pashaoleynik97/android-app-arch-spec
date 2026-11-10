# :input-api — hardware-input contract

The leaf contract of the hardware-input stack (`InputDriver`, `InputDriverProvider`, `InputEvent`, `Detection`,
`InputDriverState`). It has no Hilt, no Compose and no vendor code. Screens never use it directly — they inject
`InputController` from `:input` and collect `controller.events` with `CollectInputWhileResumed`.

```
:app ──▶ :input ──api──▶ :input-api ◀── :input-<vendor>     (implementation, release + debug)
  └────────────────────────────────────◀── :input-debug      (debugImplementation only)
```

Rules: `.arch-spec/spec/specs/recommended/hardware-input.md` (INPUT-01 …); skill reference
`application-architecture/references/hardware-input.md`.

## Adding a vendor module

1. **Create the module** `:input-<vendor>` (package `{{ROOT_PACKAGE}}.input.<vendor>`): `build.gradle.kts` with the
   `{{CONVENTION_PREFIX}}.android.library` + `{{CONVENTION_PREFIX}}.android.hilt` conventions, `.gitignore` with
   `/build`, `include(":input-<vendor>")` in `settings.gradle.kts`. Depend on `:input-api` (and `:lib-common` for
   `Reporter`) **only** — never on `:input`, so the vendor SDK stays inside this module.
2. **Implement `InputDriver`**: map the vendor's intent/SDK callbacks to `InputEvent` inside the driver (vendor
   code ids → your normalized `kind` strings; everything raw into `extras` for diagnostics). `activate` claims,
   `deactivate` releases, both idempotent and safe in any order; `events` is a hot, replay-free
   `MutableSharedFlow(extraBufferCapacity = 8)` fed with `tryEmit` (log a dropped event); a failed claim releases
   what it took and sets `InputDriverState.Error`.
3. **Implement `InputDriverProvider`**: a stable `id` (persisted — never rename it once shipped), a `displayName`,
   a `priority` (> 0 for real hardware; the debug driver is −100), and capability-based `detect()` —
   `context.isPackageInstalled("<vendor service package>")`, with `Build.MANUFACTURER` only in the evidence text.
4. **Bind it into the set**: in the module's own Hilt module,
   `@Binds @IntoSet abstract fun bind(impl: VendorInputProvider): InputDriverProvider`.
5. **Declare what the vendor needs in the vendor module's own `AndroidManifest.xml`** — the manifest merger
   carries it into the app only when the module is present: a `<queries><package android:name="<vendor service
   package>" /></queries>` block (without it `isPackageInstalled` is always `false` on Android 11+ and Auto never
   picks the driver), plus any `<uses-permission>` the vendor API requires.
6. **Add it to the host**: `implementation(project(":input-<vendor>"))` in `app/build.gradle.kts` (with a comment
   saying why), a row in the CLAUDE.md module table, and JVM tests for the pure parts (the kind-mapping table,
   intent-extra parsing). Verify on the device: `Auto` picks the vendor, the readings arrive, and pausing the app
   releases the hardware for other apps.
