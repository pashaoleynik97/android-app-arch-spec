# Engineering menu — hidden diagnostics (component: engineering-menu)

A searchable diagnostics screen whose rows are **data contributed by the modules that own them**. Modules:
`:engineering-ui-api` (`{{ROOT_PACKAGE}}.engineering.ui.api`) and `:engineering-ui` (`{{ROOT_PACKAGE}}.engineering.ui`),
no domain module. Spec: `.arch-spec/spec/specs/recommended/engineering-menu.md` (ENG-*).

Contents: 1. Contract · 2. Rules · 3. Wiring an entry · 4. Contributing a section · 5. Testing

---

## 1. Contract (`:engineering-ui-api`)

```kotlin
object EngineeringRoutes { const val MENU = "engineering/menu" }
interface EngineeringSection { val key: String; val title: String; val order: Int /* default 100 */
                               val rows: Flow<List<EngineeringRow>> }
data class EngineeringRow(key, title, description = "", control: EngineeringControl = None, enabled = true)
sealed interface EngineeringControl {
    None; Navigate(route: String); Toggle(checked, onCheckedChange: (Boolean) -> Unit)
    Picker(options: List<EngineeringOption>, selectedKey: String?, onSelect: (String) -> Unit)
}
class TapSequenceDetector(requiredTaps = 7, maxGapMillis = 600) { fun onTap(nowMillis: Long): Boolean }
```
Built in: the "Device" section (order 0: device, Android, app version, package). With `hardware-input` installed:
"Hardware input" (order 20: driver picker, active driver + state, detection per provider, last reading masked).

## 2. Rules

**ENG-01** — A trio without domain module, registered like any feature, shipped in release too; rows the build cannot
honour are disabled with the reason, not hidden. *Why:* diagnostics are needed on production builds.
<!-- origin: arch-spec ENG-01 -->

**ENG-02** — Content is data: modules contribute `@IntoSet EngineeringSection`; the menu declares `@Multibinds`.
*Why:* components add/remove their rows with no edit to the menu.
<!-- origin: arch-spec ENG-02 -->

**ENG-03** — Controls act on the live row looked up at tap time; disabled rows ignore input. *Why:* rows re-emit;
captured callbacks go stale.
<!-- origin: arch-spec ENG-03 -->

**ENG-04** — Rows are collected only while the menu is visible; a section that has not emitted is simply absent.
*Why:* no cost in the back stack; one slow contributor never blanks the menu.
<!-- origin: arch-spec ENG-04 -->

**ENG-05** — Search is pure over rendered strings (title/description, trimmed, ignore case, empty sections dropped).
*Why:* it matches what the engineer reads.
<!-- origin: arch-spec ENG-05 -->

**ENG-06** — English-only plain-string rows; secrets masked even here. *Why:* engineer-facing; screenshots travel.
<!-- origin: arch-spec ENG-06 -->

**ENG-07** — No ordinary navigation entry; a host-supplied nullable route seam opened from a hidden gesture or a
debug-only app-menu option — never a magic credential in a sign-in form. *Why:* screens stay buildable without the
menu; a credential path couples sign-in to diagnostics and can leak to the server.
<!-- origin: arch-spec ENG-07 -->

**ENG-08** — Device identity through `DeviceInfoProvider`; version from `PackageManager`. *Why:* JVM-testable; a
library cannot see `:app`'s `BuildConfig`.
<!-- origin: arch-spec ENG-08 -->

**ENG-09** — Integration between two optional components compiles conditionally on the module list
(`rootProject.findProject(":x") != null` → add a Kotlin source dir via the typed `LibraryExtension` +
`implementation(project(":x"))`), on the side with the legal dependency direction. *Why:* every component builds alone
in any install order; KSP must see the glue (variant-API source dirs are invisible to it).
<!-- origin: arch-spec ENG-09 -->

## 3. Wiring an entry

The host passes the route into the feature that shows the gesture (MOD-18):

```kotlin
// :app App.registerFeatures()
{{ROOT_PACKAGE}}.home.ui.HomeFeatureImpl(engineeringRoute = EngineeringRoutes.MENU).addToRegistry()

// :home-ui FeatureImpl → screen
class HomeFeatureImpl(private val engineeringRoute: String? = null) : HomeFeatureApi { /* pass a lambda */ }
HomeScreen(onOpenEngineering = engineeringRoute?.let { route -> { navController.navigate(route) } })

// screen: 7 taps on the version line (a monotonic clock)
val detector = remember { TapSequenceDetector() }
Text(versionText, Modifier.clickable(enabled = onOpenEngineering != null) {
    if (detector.onTap(SystemClock.uptimeMillis())) onOpenEngineering?.invoke()
})

// or, debug builds only, an app-menu option
if (BuildConfig.DEBUG && onOpenEngineering != null) {
    SetAppMenuOptions(remember(onOpenEngineering) { listOf(AppMenuItem("Engineering", onOpenEngineering)) })
}
```
The feature module never depends on `:engineering-ui*`; `null` leaves the gesture inert.

## 4. Contributing a section

```kotlin
class SyncEngineeringSection @Inject constructor(private val sync: SyncSettings) : EngineeringSection {
    override val key = "sync"; override val title = "Sync"; override val order = 40
    override val rows = sync.wifiOnly.map { wifiOnly ->
        listOf(EngineeringRow("wifi-only", "Sync on Wi-Fi only",
            control = EngineeringControl.Toggle(wifiOnly) { sync.setWifiOnly(it) }))
    }
}
@Module @InstallIn(SingletonComponent::class)
internal abstract class SyncEngineeringModule {
    @Binds @IntoSet abstract fun bind(impl: SyncEngineeringSection): EngineeringSection
}
```
Bind it in a module that may depend on `:engineering-ui-api` (a feature's `-ui` module), or in `:engineering-ui`
behind ENG-09 when the owner is infra (as the hardware-input section does in `src/withInput`). Callbacks must be
non-blocking: launch persistence on the owner's scope (repositories expose fire-and-forget setters).

## 5. Testing

**ENG-10** — VM tests with fake sections the test drives + a recording `UiCallbacks`; contributed sections tested with
their collaborators' fakes. *Why:* everything but drawing is JVM-testable.
<!-- origin: arch-spec ENG-10 -->

Reference: `engineering-ui/src/test/…` (`EngineeringMenuViewModelTest`, `EngineeringMenuFilterTest`,
`DeviceInfoSectionTest`), `engineering-ui/src/testWithInput/…/InputEngineeringSectionTest.kt` (compiled only with
hardware-input), `engineering-ui-api/src/test/…/TapSequenceDetectorTest.kt`. The VM uses `WhileSubscribed`, so tests
keep a collector running (`backgroundScope.launch { vm.uiState.collect {} }` with
`runTest(mainDispatcherRule.dispatcher)`).
