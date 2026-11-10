# ADR-0008 — Separate, qualified HTTP clients per concern

Status: accepted · Area: DI-10, DI-12 / Recommended `network`, `auth-session`, `connectivity`, `images`

## Context

A single shared `OkHttpClient` with an auth interceptor and a token authenticator would apply token refresh to
the login and refresh calls themselves (a 401 on refresh triggering another refresh), make a health probe depend
on session state, and force one timeout policy on calls with very different needs.

## Decision

Each concern owns its own qualified client, built in its own module (up to four in a full installation):

| Client | Owner | Interceptors | Why separate |
|---|---|---|---|
| auth client | `auth-session` | logging only | login/refresh must never trigger refresh (no circular refresh) |
| API client | `network` | bearer → event reporting → logging; token authenticator | every business endpoint |
| connectivity client | `connectivity` | none, short timeouts | health probe independent of session |
| files client | `images` | cookie/header auth → logging | file host authenticates differently; failures degrade to placeholders |

The business `Retrofit` is qualified; one shared `Json` configuration is provided once.

## Consequences

- No circular refresh, no session-dependent connectivity verdict, per-concern timeouts.
- Interceptor order on the API client is load-bearing and documented next to the builder.
- A connection pool per client costs a little memory — acceptable for at most four.
