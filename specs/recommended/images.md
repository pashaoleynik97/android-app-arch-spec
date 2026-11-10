# Recommended: images — server files and image loading (IMG)

Tier: **Recommended** (component `images`, depends on `network`). SHOULD / MAY guidance with a *why* per rule.

The component adds two modules: `:files` (`{{ROOT_PACKAGE}}.files`, infra, no UI — `FilesConfig`, `FileUrlResolver`,
the `@FilesClient` OkHttp client and its pluggable `FilesAuthorizer`) and `:images` (`{{ROOT_PACKAGE}}.images` — the
app's single Coil `ImageLoader` over that client, registered as Coil's singleton at process start, plus
`<Prefix>RemoteImage` and `<Prefix>Avatar`), and the host's `FilesConfigModule`. With `auth-session` installed, files
requests carry the session's bearer token automatically.

## When to use

- The app shows images (avatars, item photos, attachments) served by **your** backend, especially when the files host
  requires the user's credentials.
- Several screens show remote images and should share one cache and one configuration.

## When not to

- Only public CDN images with no auth: plain Coil with its default loader is enough (you may still want the URL
  resolver).
- Images bundled with the app: resources / vector drawables.
- Heavy media (video streaming, large downloads): use a download manager or a player stack, not an image loader.

---

## 1. Modules and configuration

**IMG-01** — Server-file concerns SHOULD be split into an infra module without UI (`:files`: config, URL resolver,
authenticated client) and a UI module (`:images`: Coil loader, composables). Domain modules depend on `:files` only;
UI modules on `:images`.
*Why:* a domain resolving a URL must not compile Compose and Coil; Coil is a UI concern.

**IMG-02** — `FilesConfig` (base URL + files path) SHOULD be its own config class, provided by the **host**
(`app/di/FilesConfigModule.kt`, mapped from the network component's `NetworkConfig` while files share the API host).
*Why:* the host owns environment config (MOD-15) and the mapping between two infra configs (MOD-16); a separate class
lets files move to a CDN by changing one provider.

**IMG-03** — URLs SHOULD be built by `FileUrlResolver` in the domain/VM layer from the bare names the API returns;
a null or blank name SHOULD resolve to `null` so the UI shows its placeholder without a request.
*Why:* one place knows the URL scheme; screens never concatenate hosts; a request for a known-missing image is waste.

## 2. The client

**IMG-04** — Files SHOULD be fetched through their own qualified client (`@FilesClient`), never the business
`@ApiClient`, with credentials attached by **at most one** `FilesAuthorizer` (`@Multibinds` set, empty = no
credentials). Strategies: bearer header or cookie; the token is read **per request** from an in-memory value. The
session integration SHOULD be compiled conditionally on the session module's presence (ENG-09).
*Why:* image fetches must not trigger the API's 401 → refresh → retry machinery or count as user activity; the auth
scheme of a files host often differs (cookies for `<img>`-friendly hosts); per-request reads pick up token rotation;
an absent session must leave the module buildable and the app working (public images load, protected ones show
placeholders).

**IMG-05** — The debug HTTP logging interceptor SHOULD be added **after** the authorizer and only in debug builds.
*Why:* a failed image load is diagnosed from the logged request headers and status; release must not log.

## 3. Loading

**IMG-06** — The app SHOULD have **one** Coil `ImageLoader` (Hilt `@Singleton`, built over `@FilesClient`) registered
as Coil's singleton with `SingletonImageLoader.setSafe { … }` from an `androidx.startup` initializer of the images
module, resolving the loader lazily through a Hilt `@EntryPoint`. Feature code SHOULD NOT build loaders.
*Why:* plain `AsyncImage(url)` everywhere then sends the files credentials; one loader = one memory and disk cache;
registering from the module's manifest needs no edit to the host's `App` (MOD-03), and `setSafe` with a lazy factory
never replaces a loader that already exists and touches Hilt only on first use.

**IMG-07** — Library versions coupled to the Kotlin compiler SHOULD be pinned with the reason next to the version:
Coil is pinned to the last release built against the project's Kotlin line (BUILD-39).
*Why:* a newer Coil pulls a stdlib whose metadata the compiler cannot read; the build fails across every module that
sees Coil, far from the cause.

## 4. Composables

**IMG-08** — Remote images SHOULD render through `<Prefix>RemoteImage(url, …, placeholder)`, which shows the same
placeholder while loading, on failure and for a `null` URL; avatars through `<Prefix>Avatar(imageUrl, name)` with an
**initials** fallback (first letters of the first and last words, code-point safe).
*Why:* an image that cannot load is not an error the user can act on; a stable placeholder avoids layout jumps; naive
initials break on extra whitespace and non-ASCII names.

## 5. Testing

**IMG-09** — The resolver SHOULD be unit-tested (slashes, encoding, null/blank), the client tested **as built by its
real `@Provides` function** against MockWebServer (no credentials without an authorizer; bearer and cookie attached;
token rotation picked up; blank token → nothing; two authorizers fail fast), and pure UI helpers (initials) tested.
*Why:* auth headers on the wrong client or with a stale token fail silently as grey placeholders.

## Adapting this to your app

- **Example-only:** the files path `v1/files/` (`FilesConfig.filesPath`) and bearer as the default scheme. A cookie
  host: change the `src/withAuth` provider in `:files` to `FilesAuthorizer.cookie("<name>") { … }`.
- **CDN:** give `FilesConfigModule` its own base URL (a build-config field per build type) and, if the CDN needs no
  credentials, delete `src/withAuth` (or bind nothing).
- **Signed URLs** (pre-signed, expiring): the API returns full URLs; skip `FileUrlResolver` for them and bind no
  authorizer — never add the session token to a third-party host.
- **Placeholders and avatar style** follow your design; keep the null / loading / error equivalence.
