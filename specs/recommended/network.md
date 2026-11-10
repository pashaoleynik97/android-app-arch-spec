# Recommended: network — the business HTTP stack (NET)

Tier: **Recommended** (component `network`). SHOULD / MAY guidance, each rule with a *why*.
Placeholders: `{{BASE_URL_DEBUG}}`, `{{BASE_URL_RELEASE}}`.

The Core rules for services, sealed results and error handling are in `../60-data-and-errors.md`
(DATA-10 … DATA-24); this component ships the transport they run on and the *example contract* they classify.
Client separation per concern: ADR-0008.

## When to use

- The app talks to an HTTP/JSON backend from more than one feature.
- You want every feature to build its Retrofit API from one client whose behaviour (credentials, refresh,
  response observers, logging) is decided in one place.

## When not to

- No backend, or only a third-party SDK that owns its own networking.
- A GraphQL / gRPC stack: keep the rules (one client, classified results, one serializer), replace the payload.

## What it adds

`:network` (`{{ROOT_PACKAGE}}.network`): `NetworkConfig` + its one provider, `@ApiClient` OkHttp,
`@ApiRetrofit` Retrofit, the shared `Json`, the extension seams `ApiInterceptor` (set) and
`@ApiClient Set<Authenticator>`, the example envelope DTOs, `parseApiError` / `parseApiErrorCode` /
`peekApiErrorCode`, `classifyCall`, `HttpStatus`, `RefreshPolicy`, and debug-only HTTP logging
(`reporterHttpLoggingInterceptor`, `HttpLogChunker`, `HttpLogMasking`, `HttpLogSettingsRepository`). Skill
reference: `application-architecture/references/network.md`.

---

## 1. Environment and clients

**NET-01** — The base URL SHOULD be set in exactly one place: `network/build.gradle.kts` writes
`BuildConfig.BASE_URL` per build type, `NetworkConfigModule` turns it into the only `NetworkConfig` binding, and
every module that talks to the same backend injects `NetworkConfig` instead of declaring its own URL.
`NetworkConfig` has no default and rejects a URL without a trailing `/`.
*Why:* MOD-15 asks for a single, host-owned environment; a build file the installer renders keeps that single
place without editing host code, and a config class without defaults makes a missing binding a build error
instead of a request to a placeholder host.

**NET-02** — Every feature endpoint SHOULD go through the `@ApiClient` client and the `@ApiRetrofit` Retrofit
(`@Provides fun api(@ApiRetrofit r: Retrofit): XApi = r.create()`). Clients for other concerns (session,
health probe, file host) are separate and qualified (ADR-0008).
*Why:* one client is where credentials, refresh and response observers apply uniformly; a feature with its own
client silently loses them.

**NET-03** — The app SHOULD have exactly one `Json` (`ignoreUnknownKeys = true`, `explicitNulls = false`),
provided once by this module and injected everywhere (DATA-20, DI-12).
*Why:* a second unqualified `Json` collides in `:app`; two configurations drift until one module rejects a
payload the other accepts.

## 2. Extension seams

**NET-04** — Optional modules SHOULD decorate the business client only through its DI seams:
`@Binds @IntoSet ApiInterceptor` (an application interceptor with an `order` — credentials band 100, observers
band 200) and `@Binds @IntoSet @ApiClient Authenticator` (at most one). Both sets are `@Multibinds`; more than one
authenticator fails at startup.
*Why:* the network module must build and run without a session (MOD-03); a seam keeps the dependency pointing
from the optional module to this one, never back.

**NET-05** — Debug logging SHOULD be the **last** interceptor on every client, added only when
`BuildConfig.DEBUG`.
*Why:* last means the logged request shows the headers contributors attached; release builds must neither pay
for body buffering nor risk logging it.

**NET-06** — Interceptors and authenticators that need the error code SHOULD read it with
`peekApiErrorCode(response, json)` (`peekBody`, capped), never by consuming the body.
*Why:* the service that classifies the same response reads the single-read error body afterwards (DATA-14);
a consumed body turns its answer into "no code" and misclassifies the response.

## 3. Classification helpers

**NET-07** — A `RetrofitXService` SHOULD wrap each call in `classifyCall(dispatchers.io, call, onFailure) { r -> … }`
and write only its status ladder; parse the error body at most once with `parseApiErrorCode` /
`parseApiError`.
*Why:* the helper is where main-safety, the `IOException` / `SerializationException`-only catch and
cancellation propagation (DATA-08, DATA-13) are implemented once instead of being re-typed — and forgotten — in
every service.

**NET-08** — API methods that can be called both by a timer and by the user SHOULD take
`@Tag policy: RefreshPolicy`; background callers pass `NeverRefresh` (DATA-19). The tag is declared in this
module so features do not depend on the session module.
*Why:* a poll that refreshes on 401 keeps an idle device signed in forever; declaring the tag here keeps the
feature's API independent of whether a session module is installed.

**NET-09** — Infra helpers SHOULD expose raw wire codes (`String?`), never feature vocabulary (DATA-24); the
envelope DTOs are the only shared wire types, features declare their own `data` payloads.
*Why:* a shared module that knows feature codes becomes the module every feature must edit.

## 4. HTTP logging

**NET-10** — Every HTTP log line SHOULD be masked (`HttpLogMasking`: secret JSON fields and credential headers)
**before** it is chunked and handed to the `Reporter` (KERNEL-01, KERNEL-05). Extend the field list with your
backend's secret names.
*Why:* external reporters receive debug output too; masking before chunking means a split cannot cut a secret
in two and hide it from the mask.

**NET-11** — Long messages SHOULD go through `HttpLogChunker`: one UTF-8-byte-budgeted entry with a
"truncated" hint by default, or numbered parts up to a ceiling when `HttpLogPolicy.printFullBodies` is on, paced
by a millisecond per part. The policy is read **per message** from `HttpLogSettingsRepository`.
*Why:* logcat silently drops what does not fit one entry (counted in bytes, not characters) and drops bursts;
reading per message lets a diagnostics toggle apply to the next request.

**NET-12** — The logging policy store SHOULD be a plain preferences file that survives sign-out and degrades to
defaults on unknown values (KERNEL-17).
*Why:* it is a device-local diagnostic switch, not a secret; a developer who turned it on expects it to stay on.

---

## Adapting this to your app

- **Example-only — adapt in code:** the envelope (`ApiResponse` / `ApiErrorResponseDto` in `dto/ApiEnvelope.kt`),
  the parse helpers' body shape, the masked field names, the timeouts. The mitm-testing fake backend serves the
  same shapes — change both together.
- **Environment:** answer the base-URL questions, or later `render.py set BASE_URL_DEBUG=…` +
  `render.py rerender BASE_URL_DEBUG BASE_URL_RELEASE` (re-renders `network/build.gradle.kts` if unmodified).
  Per-flavor URLs: move `NetworkConfigModule` to `:app/di/` and delete this one.
- **No session yet:** nothing to do — the seams resolve empty and requests go out without credentials.
- **Keep:** one client per concern, one `Json`, seams instead of edits, peek-don't-consume, logging last and
  debug-only.
