# Network — the business HTTP stack (component: network)

Recommended guidance (SHOULD) from `.arch-spec/spec/specs/recommended/network.md` (`NET-*`), building on the Core service
rules in the data-and-errors reference (DATA-10 … DATA-24). Module `:network`, package `{{ROOT_PACKAGE}}.network`.

Contents: 1. What lives where · 2. Environment · 3. Clients and seams · 4. Writing a service ·
5. Logging · 6. Testing · 7. Changing it

---

## 1. What lives where

| Need | Use |
|---|---|
| base URL | inject `NetworkConfig` (never a string constant) |
| a feature API | `@Provides fun api(@ApiRetrofit r: Retrofit): XApi = r.create()` in the feature's domain module |
| JSON | inject the one `Json` |
| envelope / error | `ApiResponse<T>`, `ApiErrorResponseDto`, `parseApiError`, `parseApiErrorCode`, `peekApiErrorCode` |
| classifier skeleton | `classifyCall(dispatcher, call, onFailure) { response -> rung }`, `HttpStatus` |
| background vs user call | `@Tag policy: RefreshPolicy` on the API method |
| add behaviour to the client | contribute an `ApiInterceptor` or an `@ApiClient` authenticator (section 3) |
| HTTP logging on a new client | `reporterHttpLoggingInterceptor(where, settings)`, debug only, last |

## 2. Environment

**NET-01** — The base URL is set once: `network/build.gradle.kts` writes `BuildConfig.BASE_URL` per build
type; `NetworkConfigModule` is the only `NetworkConfig` provider; every module talking to this backend injects
`NetworkConfig`. It has no default and must end with `/`. *Why:* one place chooses the environment (MOD-15); a
missing binding fails the build instead of calling a placeholder host.
<!-- origin: arch-spec NET-01 -->

Change the URL by editing the build file, or through the installer so the answer is recorded:

```
python3 .arch-spec/spec/installer/render.py --target . set BASE_URL_DEBUG=https://api.example.test/
python3 .arch-spec/spec/installer/render.py --target . rerender BASE_URL_DEBUG BASE_URL_RELEASE
```

Per-flavor URLs: move `NetworkConfigModule` into the host's `di` package and delete the original (one binding
only).

## 3. Clients and seams

**NET-02** — Feature endpoints go through `@ApiClient` / `@ApiRetrofit` only; other concerns (session,
health probe, file host) own separate qualified clients (ADR-0008). *Why:* credentials, refresh and response
observers apply to every feature call only if every feature call uses the one client.
<!-- origin: arch-spec NET-02 -->

**NET-03** — One `Json` (`ignoreUnknownKeys`, `explicitNulls = false`), provided by `NetworkModule`, injected
everywhere. *Why:* a second unqualified `Json` collides in the host (DI-12); two configurations drift.
<!-- origin: arch-spec NET-03 -->

**NET-04** — Optional modules decorate the client only through DI: `@Binds @IntoSet ApiInterceptor` (with an
`order`: credentials band `ApiInterceptor.ORDER_CREDENTIALS`, observers `ApiInterceptor.ORDER_OBSERVERS`) and
`@Binds @IntoSet @ApiClient Authenticator` (at most one — a second fails at startup). Both sets are
`@Multibinds`. *Why:* this module builds and runs without any session (MOD-03); edges point from the optional
module to this one.
<!-- origin: arch-spec NET-04 -->

```kotlin
@Module @InstallIn(SingletonComponent::class)
internal abstract class SessionModule {
    @Binds @IntoSet abstract fun bearer(impl: BearerInterceptor): ApiInterceptor          // order = ORDER_CREDENTIALS
    @Binds @IntoSet @ApiClient abstract fun refresher(impl: TokenRefresher): Authenticator
}
```

**NET-05** — Debug logging is the last interceptor of every client, added only `if (BuildConfig.DEBUG)`.
*Why:* the log shows the headers actually sent; release never buffers or logs bodies.
<!-- origin: arch-spec NET-05 -->

**NET-06** — Interceptors and authenticators read an error code with `peekApiErrorCode(response, json)` —
`peekBody`, capped — never by consuming the body. *Why:* the service classifying the same response reads the
single-read error body afterwards (DATA-14).
<!-- origin: arch-spec NET-06 -->

Interceptor order on the business client: contributors by `order`, then the authenticator runs underneath them
(so every contributor sees the **final** response, after a refresh-retry), then logging last.

## 4. Writing a service

**NET-07** — A Retrofit-backed service wraps each call in `classifyCall` and writes only its ladder; the error body is
parsed at most once, into a local. *Why:* main-safety, the `IOException` / `SerializationException`-only catch
and cancellation propagation are implemented once, not re-typed per service (DATA-08, DATA-13).
<!-- origin: arch-spec NET-07 -->

```kotlin
/** Ladder: 2xx+mappable → Success · 2xx unmappable → Transient · 401 → SessionExpired ·
 *  403 → Refused(code) · 404 → Unavailable · else → Transient (logged). */
internal class RetrofitItemsService @Inject constructor(
    private val api: ItemsApi, private val dispatchers: DispatcherProvider, private val json: Json,
) : ItemsService {
    private val logTag = logTag()

    override suspend fun item(id: String, policy: RefreshPolicy): ItemResult = classifyCall(
        dispatcher = dispatchers.io,
        call = { api.item(id, policy) },
        onFailure = { ItemResult.Transient(it) },
    ) { r ->
        when {
            r.isSuccessful -> r.body()?.data?.toDomain()?.let(ItemResult::Success)
                ?: ItemResult.Transient(null).also { Reporter.error("GET item: unusable 2xx", where = logTag) }
            r.code() == HttpStatus.UNAUTHORIZED -> ItemResult.SessionExpired
            r.code() == HttpStatus.FORBIDDEN -> ItemResult.Refused(parseApiErrorCode(r, json))
            r.code() == HttpStatus.NOT_FOUND -> ItemResult.Unavailable
            else -> ItemResult.Transient(null).also { Reporter.error("GET item → ${r.code()}", where = logTag) }
        }
    }
}
```

**NET-08** — API methods reachable from a timer take `@Tag policy: RefreshPolicy`; background callers pass
`RefreshPolicy` NeverRefresh (DATA-19). The tag lives here so a feature never depends on the session module.
*Why:* a poll that refreshes on 401 keeps an idle device signed in forever.
<!-- origin: arch-spec NET-08 -->

**NET-09** — This module exposes raw wire codes (`String?`) only; each feature maps codes to its own vocabulary
and declares only its own `data` payload DTOs. *Why:* a shared module that knows feature codes becomes the module
every feature edits (DATA-24).
<!-- origin: arch-spec NET-09 -->

## 5. Logging

**NET-10** — Every HTTP log line is masked (`HttpLogMasking`: secret JSON fields, credential headers) before it
is chunked and reported. Add your backend's secret field names to `HttpLogMasking.SECRET_FIELDS`. *Why:*
KERNEL-05 — reporters may forward debug output; masking first means a split cannot hide half a secret.
<!-- origin: arch-spec NET-10 -->

**NET-11** — Long messages go through `HttpLogChunker` under the `HttpLogPolicy` read per message from
`HttpLogSettingsRepository`: one byte-budgeted entry with a "truncated" hint by default; numbered parts up to
`HttpLogLimit` when `printFullBodies` is on. *Why:* logcat drops what does not fit one entry (in bytes) and drops
bursts; per-message reads make a diagnostics toggle apply to the next request.
<!-- origin: arch-spec NET-11 -->

**NET-12** — The logging policy is a plain preferences file that survives sign-out and falls back to defaults
on unknown values. *Why:* a device-local diagnostic switch, not a secret (KERNEL-17).
<!-- origin: arch-spec NET-12 -->

A diagnostics screen toggles it with `setPrintFullBodies` / `setLimit` and disables the controls when
`HttpLogSettingsRepository.isAvailable` is false (release).

## 6. Testing

- Services: MockWebServer + real Retrofit + the real converter (TEST-20) — every rung, a 2xx without a body,
  an undecodable 2xx, a dropped connection (`SocketPolicy.DISCONNECT_AT_START`), and that cancellation
  propagates. Use the exact JSON your backend (or the fake backend) returns.
- The client: build it with `NetworkModule.provideApiOkHttpClient(interceptors, authenticators, settings)` — a
  plain function — and fake contributors; assert order and the authenticator path.
- Interceptors that peek: assert the body is still readable by the caller afterwards.
- Pure helpers (`HttpLogChunker`, `HttpLogMasking`): table tests.

Run: `./gradlew :network:testDebugUnitTest`.

## 7. Changing it

- The envelope is an **example contract** (`dto/ApiEnvelope.kt`); adapt it, the parse helpers and the fake
  backend together.
- New global behaviour on the business client = a new `ApiInterceptor` contribution, never an edit of
  `NetworkModule`.
- New client for a new concern: its own module, its own qualifier, `reporterHttpLoggingInterceptor` last, the
  shared `Json`.
