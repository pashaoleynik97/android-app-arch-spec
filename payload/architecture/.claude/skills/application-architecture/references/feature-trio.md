# Feature trio — contract, UI and domain modules, host seams (component: architecture)

Spec: `.arch-spec/spec/specs/10-modules.md` §3 and §6. Golden rules in `SKILL.md`: MOD-08 (three modules),
MOD-11 (no edges between features). To create a feature use the **add-feature** skill; the reference
feature is `home` (`:home-ui-api`, `:home-ui`, `:home`).

Contents: 1. The three modules · 2. Package layout · 3. Registration · 4. Seams between siblings ·
5. Checklist

---

## 1. The three modules

| Module | Package | Holds | Depends on |
|---|---|---|---|
| `x-ui-api` | `{{ROOT_PACKAGE}}.x.ui.api` | `XFeatureApi : FeatureApi`, `object XRoutes`, small link types | `api(project(":feature-api"))` only |
| `x-ui` | `{{ROOT_PACKAGE}}.x.ui` | `XFeatureImpl`, screens, ViewModels, contracts, strings, drawables | `:lib-android`, `x-ui-api`, `x` |
| `x` | `{{ROOT_PACKAGE}}.x.domain` | interactors, models, services, DTOs, stores, its Hilt module | `:lib-common` + the infra it uses |

**MOD-09** — The contract module is an Android library with the library convention only — no Compose
convention, no Hilt — and exactly one dependency, `api(project(":feature-api"))`. *Why:* a contract that pulls
Compose or Hilt makes every consumer (the host, every sibling that links to it) pay for it.
<!-- origin: arch-spec MOD-09 -->

**MOD-10** — The domain module uses no Compose, ViewModel, lifecycle or navigation types. It exposes
interfaces (interactor, service, store) that the UI module fakes in tests, returns feature-level sealed
outcomes (never HTTP types), and owns its Hilt module (`…/domain/di/XDomainModule.kt`). *Why:* the ViewModel
branches on outcomes only; keeping HTTP and persistence out of the UI module keeps ViewModel tests pure.
<!-- origin: arch-spec MOD-10 -->

## 2. Package layout

**MOD-12** — One predictable place per role:

```
x-ui-api  …/x/ui/api/XFeatureApi.kt, XRoutes.kt, [XLink.kt]
x-ui      …/x/ui/XFeatureImpl.kt
          …/x/ui/<screen>/XScreen.kt, XViewModel.kt, XViewModelContract.kt, components/*.kt
          …/x/ui/components/*.kt                     (shared within the feature)
          src/main/res/values/strings.xml            (keys prefixed with the feature id)
x         …/x/domain/XInteractor.kt, DefaultXInteractor.kt
          …/x/domain/model/XModels.kt
          …/x/domain/network/{XApi, XService, RetrofitXService, XMapper}.kt, dto/XDtos.kt   (with a backend)
          …/x/domain/di/XDomainModule.kt
```
*Why:* an agent finds and extends code without searching; the feature template renders exactly this shape
(the reference feature's screen package is `screen`).
<!-- origin: arch-spec MOD-12 -->

## 3. Registration

**DI-15** — Features are registered explicitly in the host Application's `registerFeatures()` (inside the
`arch-spec:feature-registrations` marker block): `XFeatureImpl(…seams…).addToRegistry()`. The NavHost iterates
`FeaturesHolder.features()`. Registration is not done through DI. *Why:* the explicit call is where host
seams (section 4) are filled; a Hilt `@IntoSet FeatureApi` multibinding is the recorded scale-up path.
<!-- origin: arch-spec DI-15 -->

```kotlin
// :app — App.registerFeatures()
HomeFeatureImpl().addToRegistry()
ListFeatureImpl(detailsRoute = DetailsRoutes::details).addToRegistry()
DetailsFeatureImpl(itemGoneResultKey = ListRoutes.RESULT_ITEM_GONE).addToRegistry()
```

## 4. Seams between siblings

**MOD-18** — Cross-feature navigation uses host-supplied seams: the FeatureImpl declares nullable constructor
parameters with `null` defaults, and the host fills them at registration.

| Seam | Type | Example |
|---|---|---|
| fixed route | `String?` | `homeRoute: String? = null` |
| route builder | `((id: String) -> String)?` | `detailsRoute = DetailsRoutes::details` |
| result key the sibling writes | `String?` / a result key | `itemGoneResultKey = ListRoutes.RESULT_ITEM_GONE` |
| route + key bundle | data class in the **caller's** contract module | `data class CaptureLink(val route: (String) -> String, val resultKey: String)` |

A `null` seam leaves the action inert (or routes to a local stub destination), never crashes; a route and the
result key that belong to one contract travel together in one link type. *Why:* a compile dependency between
two UI modules would break the module rules; nullable seams keep each feature runnable alone and the caller
ignorant of the callee's vocabulary.
<!-- origin: arch-spec MOD-18 -->

**MOD-19** — Sibling domain modules never depend on each other. When `a` must trigger something in `b`, `a`
declares its **own** small port (`fun interface ListInvalidator { suspend fun invalidate() }`), leaves it
unbound in its DI module (saying so in the module KDoc, DI-09), and the host binds it to `b`'s API in a host
DI module. Two features needing the same capability declare two ports. *Why:* a shared port would need a
module both can see — the very dependency the rules forbid; a per-consumer port also documents what each
feature needs.
<!-- origin: arch-spec MOD-19 -->

```kotlin
// :app — di/CrossFeatureModule.kt
@Module @InstallIn(SingletonComponent::class)
object CrossFeatureModule {
    @Provides fun listInvalidator(cache: OrdersCache): ListInvalidator = ListInvalidator { cache.refresh() }
}
```

## 5. Checklist for a feature change

- The contract module still has exactly one dependency (MOD-09).
- No `project(":<other feature>…")` line in any of the three build files (MOD-11).
- New sibling links are seams filled in `registerFeatures()` (MOD-18) or ports bound in `:app` (MOD-19).
- New routes follow UI-19 (presentation reference); a new result key is owned by the reader.
- Module lines in `CLAUDE.md` exist for all three modules (MOD-22).
