# Parameters

Every `{{PLACEHOLDER}}` used anywhere in `payload/` is defined here. `installer/render.py` reads values from the
target's `.arch-spec/state.json` (`answers` object) and substitutes them in file contents **and** paths.

**Every question is optional.** An unanswered parameter falls back to its *safe placeholder*. Safe placeholders always
render to code that compiles and runs. When a fallback is used for a parameter marked **deferrable**, the installer
records it under `deferred` in the state file, together with the "complete later" hint below.

Derived parameters are computed by `render.py` and are never asked.

## Identity

| Parameter | Meaning | How to derive a default from the empty project | Safe placeholder | Deferrable |
|---|---|---|---|---|
| `APP_NAME` | Human-readable app name (launcher label, docs) | `app/src/main/res/values/strings.xml` → `app_name` | `My App` | no |
| `PROJECT_SLUG` | Kebab-case project id (`rootProject.name`, file names, CI artifact names) | `APP_NAME`, kebab-cased (follows it until answered) | `my-app` | no |
| `APPLICATION_ID` | Play/device application id | `applicationId` in `app/build.gradle.kts` | `com.example.myapp` | no |
| `APP_PACKAGE` | Kotlin package / namespace of `:app` | = `APPLICATION_ID` (the stock namespace is recorded separately so stock files can be removed) | = `APPLICATION_ID` | no |
| `ROOT_PACKAGE` | Package root every module namespaces under (`{{ROOT_PACKAGE}}.libcommon`, …) | `APP_PACKAGE` minus its last segment (`com.acme.shop` → `com.acme`) | `com.example` | no |
| `CONVENTION_PREFIX` | Prefix of convention-plugin ids (`<prefix>.android.library`) and catalog aliases | last segment of `ROOT_PACKAGE` | `app` | no |
| `THEME_PREFIX` | PascalCase prefix for the design system (`<Prefix>Theme`, `<Prefix>Button`, …) | `APP_NAME` → PascalCase, letters/digits only | `App` | no |

Derived: `HEALTH_PATH_SLUG` (`HEALTH_PATH` as a mitm folder name: `v1/health` → `v1_health`), `STOCK_APP_PACKAGE_PATH` (the empty project's original namespace, recorded by `init`; used only by
`removes` globs so stock files are found even when `APP_PACKAGE` changed), `ROOT_PACKAGE_PATH` (= `ROOT_PACKAGE` with `.` → `/`), `APP_PACKAGE_PATH`, `CONVENTION_PREFIX_PASCAL`
(`acme` → `Acme`, used in plugin class names), `THEME_PREFIX_LOWER` (resource names, e.g. `ic_{{THEME_PREFIX_LOWER}}_logo`).
`REFERENCE_WIDTH_DP` / `REFERENCE_HEIGHT_DP` (the two halves of `REFERENCE_DEVICE_DP`, for `@Preview(widthDp, heightDp)`
annotation arguments, which must be integer constants; `rerender REFERENCE_DEVICE_DP` refreshes them too).

## Platform

| Parameter | Meaning | Default | Deferrable |
|---|---|---|---|
| `MIN_SDK` | `minSdk` | value in the empty project, else `24` | no |
| `TARGET_SDK` | `targetSdk` | from `specs/toolchain.md` known-good set | no |
| `COMPILE_SDK` | `compileSdk` | from `specs/toolchain.md` known-good set (may be one above target, see the toolchain gotchas) | no |
| `JVM_TARGET` | Java/Kotlin bytecode target | `11` | no |
| `BASE_LOCALE` | Language of `values/strings.xml` | `en` | no |
| `EXTRA_LOCALES` | Comma-separated extra locales to scaffold `values-<x>/` for; an explicit empty answer means "none" (not deferred) | empty | yes — "add a locale later: create `values-<x>/strings.xml` per module" |

## Design (component `architecture`)

| Parameter | Meaning | Safe placeholder | Deferrable — complete later via |
|---|---|---|---|
| `COLOR_PRIMARY` | Brand primary, `#RRGGBB` | `#3D5AFE` (neutral indigo) | `INSTALL.md` → *complete deferred* → `design.palette` (re-renders `Color.kt` palette only) |
| `COLOR_ERROR` / `COLOR_SUCCESS` / `COLOR_WARNING` | Status colours | `#D32F2F` / `#2E7D32` / `#ED6C02` | same |
| `FONT_FAMILY` | `system` or the name of a bundled font family (files dropped into `lib-android/src/main/res/font/`) | `system` | `design.typography` |
| `REFERENCE_DEVICE_DP` | The smallest screen the design targets, `WIDTHxHEIGHT` in dp — previews are sized to it | `360x640` | `design.referenceDevice` |
| `THEME_MODE` | `light-only` or `light-dark` | `light-only` | `design.themeMode` |
| `FIGMA_URL` | Design source, recorded for the agent | empty | `design.figma` |

## Backend (Recommended components `network`, `auth-session`, `connectivity`, `images`, `mitm-testing`)

| Parameter | Meaning | Safe placeholder | Deferrable — complete later via |
|---|---|---|---|
| `BASE_URL_DEBUG` | API base URL for debug builds, trailing `/` | `https://api.example.invalid/` | `network.baseUrl` |
| `BASE_URL_RELEASE` | API base URL for release builds | = `BASE_URL_DEBUG` | `network.baseUrl` |
| `HEALTH_PATH` | Relative health-check path for `connectivity` | `health` | `connectivity.healthPath` |

The wire contract (envelope, error body, auth endpoints) is **not** a parameter: the `network` / `auth-session`
reference code ships an *example contract* that the developer adapts in code. See `specs/recommended/network.md`.

## CI/CD (Recommended component `ci-cd`)

| Parameter | Meaning | Safe placeholder | Deferrable — complete later via |
|---|---|---|---|
| `DEFAULT_BRANCH` | Protected integration branch | `main` | no |
| `KEYSTORE_FILE` | Release keystore file name at repo root (gitignored) | `{{PROJECT_SLUG}}-release.jks` | `ci.signing` — until then builds fall back to the debug key |
| `CI_BOT_PUSH` | `github-app` (version-bump commits pushed by a GitHub App on the branch-ruleset bypass list) or `none` (no auto bump; versionCode from commit count) | `none` | `ci.bot` |
| `JDK_CI` | JDK used on CI runners | from `specs/toolchain.md` | no |

## Feature scaffolding (used by `architecture/feature-template` and the `add-feature` skill)

| Parameter | Meaning | Example |
|---|---|---|
| `FEATURE` | kebab-case feature id; module names `:{{FEATURE}}`, `:{{FEATURE}}-ui`, `:{{FEATURE}}-ui-api` | `order-history` |
| `FEATURE_PASCAL` | derived | `OrderHistory` |
| `FEATURE_CAMEL` | derived | `orderHistory` |
| `FEATURE_PKG` | derived package segment (lowercase, no dashes) | `orderhistory` |

The installer renders the template once with `FEATURE=home` (the reference feature and start destination).

## Validation rules (enforced by `render.py`)

- `APPLICATION_ID` / `APP_PACKAGE` match `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$` (two or more segments);
  `ROOT_PACKAGE` may also be a single segment; `CONVENTION_PREFIX` matches `^[a-z][a-z0-9]*$` (it becomes a Gradle
  accessor segment, so no dashes); `FEATURE` matches `^[a-z][a-z0-9-]*$`; colours match `^#[0-9A-Fa-f]{6}$`; URLs start with `https://` or `http://` and end with `/`.
- An invalid answer is rejected with the rule, and the question is asked again. It is never silently "fixed".
