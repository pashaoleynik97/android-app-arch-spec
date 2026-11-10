# 00 — Constitution

The non-negotiables of every project built from this spec. Each article is a summary; the rule it points to is
normative and carries the *why*. A project MAY override an article only through a recorded decision (an ADR in
the project, or a "Project override" block in its skill) that states the reason and date.

## I. Structure

1. **Dependencies point one way.** Kernel ← features/infra ← host; never inverted, nothing depends on `:app`.
   → MOD-01, MOD-02
2. **Every UI feature is a trio** `:x-ui-api` / `:x-ui` / `:x`; features never depend on each other.
   → MOD-08, MOD-11
3. **Siblings meet only in the host**: host-supplied navigation seams and consumer-declared ports bound by `:app`.
   → MOD-18, MOD-19, MOD-16
4. **Infra modules are standalone, UI-free, with their own qualified clients and scopes; every environment value
   is set in exactly one place** (no-default config classes). → MOD-13, MOD-15, DI-08
5. **Core never references an optional component.** Optional parts plug in through DI sets or marker inserts.
   → MOD-03, DI-13

## II. Build

6. **All versions in the catalog**; pins carry their reason. → BUILD-10, BUILD-11
7. **Shared build config only in `build-logic` convention plugins**; module build files are declarative.
   → BUILD-01, BUILD-03, BUILD-04
8. **On AGP with built-in Kotlin, never apply the standalone Kotlin Android plugin.** → BUILD-30
9. **Configuration cache stays on; file reads go through `providers`.** → BUILD-14, BUILD-41
10. **Version and signing are derived, never hand-edited; secrets are never committed; R8 is an explicit
    decision.** → BUILD-17, BUILD-18, BUILD-21, BUILD-22

## III. Code

11. **Hilt everywhere, constructor injection, interfaces with internal impls, qualified duplicates.**
    → DI-01, DI-03, DI-06, DI-12
12. **Log only through `Reporter` (`where = logTag`); never log credentials.** → KERNEL-01, KERNEL-02, KERNEL-05
13. **Suspend APIs are main-safe; cancellation always propagates.** → KERNEL-15, KERNEL-16
14. **Anything that must survive process death does not live only in memory** (ids in routes, results in the
    back-stack `SavedStateHandle`, `Scopes` only as a cache). → KERNEL-09, UI-21, ADR-0004
15. **Clocks, dispatchers and randomness are injected seams.** → KERNEL-14

## IV. Presentation

16. **Every screen: `@HiltViewModel` + `BaseViewModel` + `XViewModelContract`; private `State` →
    `toUxState()` → `StateFlow`; Context-free.** → UI-01, UI-04, UI-05, UI-06
17. **Navigation code only in `XFeatureImpl`; screens get lambdas, ViewModels get `uiCallbacks`.** → UI-08, UI-18
18. **Work tied to visibility runs only while RESUMED.** → UI-13, UI-14
19. **Hosts own global outcomes and host chrome; screens declare chrome through RESUMED-gated `Set…` effects.**
    → UI-17, UI-23, UI-24
20. **Style only from `<Prefix>Theme` tokens; one parametrized kit component per role.** → DS-03, DS-06, DS-13

## V. Quality

21. **Every non-trivial class has a JVM unit test with hand-written fakes; no mocking library.** → ADR-0002,
    `70-testing.md`
22. **Decisions are extracted into pure, tested policies.** → UI-31
23. **Rules change through the skills, in the same change as the code.** → `90-ai-workflow.md`
