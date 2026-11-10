# Dependency injection — Hilt with KSP (component: architecture)

Spec: `.arch-spec/spec/specs/30-di.md`. Golden rule in `SKILL.md`: DI-06 (interface + `internal` impl bound
with `@Binds`). Feature registration (DI-15) is in the feature-trio reference; Hilt/KSP version gotchas
(BUILD-34, BUILD-37, BUILD-40) in the toolchain reference.

Contents: 1. Entry points · 2. Where modules live · 3. Module shape · 4. Qualifiers and scopes ·
5. Collisions and optional sets · 6. Eager singletons · 7. Tests

---

## 1. Entry points

**DI-01** — Entry points are the `@HiltAndroidApp` Application, the single `@AndroidEntryPoint` Activity and
`@HiltViewModel` ViewModels (obtained with `hiltViewModel()`); nothing else is an entry point unless it truly
needs injection. *Why:* one Activity plus ViewModels covers a Compose app; extra entry points add generated
code and lifecycle surprises.
<!-- origin: arch-spec DI-01 -->

**DI-02** — Bindings are installed in `SingletonComponent`; there are no ViewModel-, Activity- or
Fragment-scoped modules. Per-screen state belongs to the ViewModel, per-flow state to Scopes. *Why:* one
component keeps the graph flat and predictable.
<!-- origin: arch-spec DI-02 -->

**DI-03** — Constructor injection is the default; field injection only on the Application and the Activity,
for DI-14. *Why:* constructor injection makes every class constructible in a plain JVM test.
<!-- origin: arch-spec DI-03 -->

## 2. Where modules live

**DI-04** — A Hilt module lives in the module that owns what it binds, at `<module>/…/di/<Name>Module.kt`
(`XDomainModule` for a feature domain, KernelModule in `:lib-common`). UI modules have no Hilt modules. The
host's `di` package holds only environment-config providers (MOD-15) and cross-sibling bindings (MOD-19).
*Why:* bindings next to their implementations keep implementations `internal`; the host wires only what nobody
else may see.
<!-- origin: arch-spec DI-04 -->

**DI-05** — Every module that declares an `@Inject` constructor, `@Module`, `@HiltViewModel` or `@Singleton`
type applies the `{{CONVENTION_PREFIX}}.android.hilt` convention. *Why:* KSP generates factories only where it
runs; a missing convention surfaces later as a confusing "cannot be provided" error in the host.
<!-- origin: arch-spec DI-05 -->

## 3. Module shape

```kotlin
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OrdersDomainModule {
    @Binds abstract fun bindOrdersInteractor(impl: DefaultOrdersInteractor): OrdersInteractor

    companion object {
        @Provides @Singleton
        fun provideOrdersConfig(): OrdersConfig = OrdersConfig(pageSize = 50)   // public (BUILD-40)
    }
}
```
A module with only `@Provides` functions is an `object` with public functions.

**DI-07** — Every seam a test fakes (interactor, service, store, cache, clock, dispatcher, OS facility) is an
interface; pure stateless helpers (validators, mappers, policies) stay concrete classes or `object`s used
directly. *Why:* interfaces where they buy a fake, concrete code where they would only add ceremony.
<!-- origin: arch-spec DI-07 -->

**DI-08** — Injected configs are `data class`es — never a value class over a primitive (BUILD-37).
Environment configs have no defaults and are provided only by the host; tuning configs may be defaulted by
their owner. *Why:* a missing environment binding must fail the build, not fall back to a placeholder.
<!-- origin: arch-spec DI-08 -->

**DI-09** — A module's KDoc lists what it deliberately leaves unbound (ports the host binds, MOD-19).
*Why:* without it the next agent "fixes" the missing binding inside the feature and creates the sibling edge
the port exists to avoid.
<!-- origin: arch-spec DI-09 -->

## 4. Qualifiers and scopes

**DI-10** — Each module that owns a client, a long-lived scope or a private dispatcher declares qualifiers in
its `…/di/Qualifiers.kt` and qualifies every OkHttp client, Retrofit, app-lifetime scope and module-private
dispatcher; a per-screen config another screen might also need is qualified per consumer.
*Why:* qualifiers make ownership explicit, let two modules provide the same type without colliding, and make
"which client does this call use?" answerable from the signature.
<!-- origin: arch-spec DI-10 -->

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class OrdersScope        // CoroutineScope
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class OrdersIoDispatcher // CoroutineDispatcher
```

**DI-11** — App-lifetime scopes are provided `@Singleton` as `CoroutineScope(SupervisorJob() + dispatcher)`
under the owning module's qualifier. `GlobalScope` and ad-hoc scopes created inside classes are forbidden.
*Why:* `SupervisorJob` keeps one failing child from cancelling the rest; an injected scope can be replaced by a
test scope driven by virtual time.
<!-- origin: arch-spec DI-11 -->

## 5. Collisions and optional sets

**DI-12** — No duplicate unqualified bindings: when two modules provide one type, all but at most one are
qualified, and singletons that must be identical (one `Json` configuration) are provided once and reused.
*Why:* duplicates compile in their own modules and fail only when the host aggregates them — the error points
at the host, not the cause.
<!-- origin: arch-spec DI-12 -->

**DI-13** — A `Set<T>` that may legitimately be empty (plugin drivers, contributions from optional components)
is declared with `@Multibinds` in the module that consumes it; contributors add with `@IntoSet`.
*Why:* without `@Multibinds` an empty set is a missing binding, and the build fails exactly in the
configuration that did not install the optional module (MOD-03).
<!-- origin: arch-spec DI-13 -->

```kotlin
@Module @InstallIn(SingletonComponent::class)
internal abstract class InputModule {
    @Multibinds abstract fun drivers(): Set<InputDriver>   // may be empty: no vendor module installed
}
```

## 6. Eager singletons

**DI-14** — Machinery that must run from process start (the foreground watch, a polling cache, a filter that
must see the first request, a stale-file sweeper) is a `@Singleton` that starts its work in `init {}` on its
own injected scope and is field-injected into the Application purely so it exists, with a comment saying
why. Activity-bound objects are injected into the Activity and registered as lifecycle observers there.
*Why:* a lazy singleton starts whenever the first screen touches it, so behaviour depends on navigation order.
<!-- origin: arch-spec DI-14 -->

```kotlin
@HiltAndroidApp
class App : Application() {
    /** Must observe the process lifecycle from startup; background jobs gate on it. */
    @Inject lateinit var appForegroundWatch: AppForegroundWatch
}
```

## 7. Tests

**DI-16** — JVM unit tests construct the class under test directly with fakes; Hilt test components are not
used in unit tests. *Why:* direct construction is faster, needs no generated code, and fails to compile when a
dependency is added — exactly when the test must be updated.
<!-- origin: arch-spec DI-16 -->
