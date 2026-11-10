# Images — server files and the image loader (component: images)

`:files` (`{{ROOT_PACKAGE}}.files`, infra, no UI) resolves file URLs and owns the authenticated `@FilesClient`;
`:images` (`{{ROOT_PACKAGE}}.images`) builds the app's one Coil loader over it and ships the image composables (see the
compose-ui skill's `images-ui.md`). Spec: `.arch-spec/spec/specs/recommended/images.md` (IMG-*).

Contents: 1. Surface · 2. Rules · 3. Auth strategies · 4. Coil singleton · 5. Toolchain · 6. Testing

---

## 1. Surface

```kotlin
data class FilesConfig(baseUrl: String, filesPath: String = "v1/files/")   // provided by app/di/FilesConfigModule.kt
class FileUrlResolver { fun fileUrl(fileName: String): String; fun fileUrlOrNull(fileName: String?): String? }
fun interface FilesAuthorizer { fun authorize(request: Request): Request
    companion object { fun bearer(token: () -> String?); fun cookie(name: String, token: () -> String?) } }
@FilesClient OkHttpClient                      // :files FilesModule
coil3.ImageLoader (@Singleton)                 // :images ImagesModule, registered as Coil's singleton
```

Dependency directions: domain → `:files`; UI → `:images` (→ `:files`, `:lib-android`); `:app` → both (Hilt).

## 2. Rules

**IMG-01** — `:files` (infra, no UI) vs `:images` (Coil + composables); domains depend on `:files` only. *Why:*
resolving a URL must not compile Compose.
<!-- origin: arch-spec IMG-01 -->

**IMG-02** — `FilesConfig` is its own class, provided by the host (mapped from `NetworkConfig` today). *Why:* host
owns config; files can move to a CDN by changing one provider.
<!-- origin: arch-spec IMG-02 -->

**IMG-03** — URLs come from `FileUrlResolver` in the domain/VM; null/blank → `null` → placeholder, no request. *Why:*
one owner of the URL scheme.
<!-- origin: arch-spec IMG-03 -->

**IMG-04** — Own `@FilesClient`, never `@ApiClient`; at most one `FilesAuthorizer` (`@Multibinds`, empty = no
credentials); token read per request; the session glue compiles only when the auth-session component is installed. *Why:* no refresh
machinery for images, per-host auth schemes, rotation picked up, buildable without a session.
<!-- origin: arch-spec IMG-04 -->

**IMG-05** — Debug-only HTTP logging, added after the authorizer. *Why:* diagnose failed loads from headers/status.
<!-- origin: arch-spec IMG-05 -->

**IMG-06** — One loader, registered with `SingletonImageLoader.setSafe` from `ImageLoaderInitializer`
(`androidx.startup`), resolved lazily via a Hilt entry point; features never build loaders. *Why:* every `AsyncImage`
authenticates, one cache, no edit to `App`.
<!-- origin: arch-spec IMG-06 -->

**IMG-07** — Coil is pinned to the Kotlin line (catalog comment, BUILD-39). *Why:* a newer Coil's stdlib metadata is
unreadable by the project's compiler.
<!-- origin: arch-spec IMG-07 -->

## 3. Auth strategies

| Installed | Behaviour |
|---|---|
| `network` only | no authorizer bound → requests unmodified; protected images show placeholders |
| `network` + `auth-session` | `files/src/withAuth/…/SessionFilesAuthModule` binds `FilesAuthorizer.bearer { authManager.currentAccessToken() }` (compiled because `files/build.gradle.kts` finds the session module) |
| a cookie host | change that provider to `FilesAuthorizer.cookie("<cookie name>") { authManager.currentAccessToken() }` |
| public CDN / signed URLs | bind nothing (delete the `src/withAuth` provider); never send the session token to a third party |

Binding a second authorizer is a wiring error: the client fails fast at construction.

## 4. Coil singleton

Coil 3 resolves its singleton lazily: a factory set by `SingletonImageLoader.setSafe` wins, else an `Application`
implementing `SingletonImageLoader.Factory`, else a default loader. `ImageLoaderInitializer` (merged into the app's
`androidx.startup` provider by `:images`' manifest) calls `setSafe { entryPoint.imageLoader() }` before
`Application.onCreate`, so no screen can create the default loader first. If the host removes
`InitializationProvider`, call `SingletonImageLoader.setSafe { … }` from `App.onCreate` instead.

Feature code: `AsyncImage(model = url, …)` or the kit composables — never `ImageLoader.Builder`.

## 5. Toolchain

Catalog section `component: images`: `coil` version + `coil-compose`, `coil-network-okhttp`; `androidx-startup-runtime`
(shared with media-store). Bump Coil only together with `kotlin` and run `assembleDebug` — the failure mode is
"incompatible metadata version" in every module that sees Coil.

## 6. Testing

**IMG-09** — Resolver unit tests; the client as built by `FilesModule.provideFilesOkHttpClient` against MockWebServer
(no authorizer, bearer, cookie, rotation, blank token, two authorizers fail); pure UI helpers (initials). *Why:* auth
on the wrong client or a stale token fails silently as grey placeholders.
<!-- origin: arch-spec IMG-09 -->

Reference: `files/src/test/…` (`FileUrlResolverTest`, `FilesClientTest`), `images/src/test/…/InitialsTest.kt`.
