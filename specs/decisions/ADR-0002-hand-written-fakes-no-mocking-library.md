# ADR-0002 — No mocking library: hand-written fakes

Status: accepted · Area: TEST (`specs/70-testing.md`), DI-07

## Context

Mocking libraries (reflection- or bytecode-based) make tests that pass while asserting on call shapes rather
than behaviour, break on Kotlin features (final classes, suspend functions, value classes, inline functions),
slow the JVM test run, and let a test silently keep passing after the real dependency changed its contract.

## Decision

No mocking library is a dependency of any module. Every seam a test needs to replace is an interface (DI-07), and
tests use **hand-written fakes** that implement the full interface: replaceable result fields, `MutableStateFlow`
drivers (`emit(...)`), recorded calls in public lists, and `CompletableDeferred` gates to hold a call in flight.
Fakes shared by several modules live in `:core-testing`; one-off fakes are private to the test file. Real HTTP
mapping is tested against MockWebServer, not mocked clients.

## Consequences

- A contract change breaks the fake at compile time — the desired moment to revisit the tests.
- Fakes cost code; they are reused across a module's tests and kept minimal (inert stubs for unused members).
- Tests read as behaviour ("a stale list is treated as no list"), not as interaction scripts.
