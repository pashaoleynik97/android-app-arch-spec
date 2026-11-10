# ADR-0009 — Activity-gated keep-alive; the server decides session death

Status: accepted · Area: Recommended `auth-session` (`specs/recommended/auth-session.md`)

## Context

With short-lived access tokens and a server-side idle window, the client can either (a) end idle sessions itself
with a local inactivity timer, or (b) only decide whether to keep extending the session. A local timer duplicates
server policy, drifts from it, and fails across process death; unconditional background refresh keeps an
abandoned device signed in forever.

## Decision

The client **never ends an idle session on its own; it only stops extending it**. A proactive refresh runs shortly
before expiry **only if the user interacted** since the last refresh (interactions stamped from
`Activity.onUserInteraction()` on a monotonic clock). With no interaction, the loop suspends until the next
interaction instead of refreshing. Background requests (polling) are tagged so they may **not** trigger a
refresh. The server's idle window decides when the session dies; the client reacts to the resulting 401.

## Consequences

- One source of truth for session lifetime (the server); no client/server timer mismatch.
- Polling cannot keep an idle session alive.
- A returning user whose session expired server-side sees the session-ended notice on their next request,
  not preemptively.
