# ADR-0005 — Channel, not SharedFlow, for app-wide event buses

Status: accepted · Area: KERNEL / Recommended `access-loss`, `auth-session`

## Context

Some infra events are produced on background threads (an OkHttp interceptor seeing a 403) and must be handled
exactly once by a chain of handlers. A `SharedFlow` with `replay = 0` **drops** emissions that happen before its
collector subscribes (e.g. during process start), and fans each emission out to every collector, so two
handlers can start competing recoveries.

## Decision

App-wide event buses with "handle exactly once" semantics are a `Channel` (bounded, `DROP_OLDEST`) drained
**sequentially** on an injected app scope. Producers call non-suspending `trySend`. Handlers are walked
last-registered-first; each call is isolated with `runCatching`; a host fallback handler always consumes.
Handlers are registered process-wide at startup (DI-14), not per screen.

`SharedFlow` remains the right tool where every subscriber should see the event or where loss is acceptable:
screen exit flows (`replay = 1`, UI-09) and host navigation events (`replay = 0`, UI-28).

## Consequences

- No event produced before the first collector is lost; two failures cannot trigger two recoveries.
- A slow handler delays later events — handlers must be quick and delegate long work.
