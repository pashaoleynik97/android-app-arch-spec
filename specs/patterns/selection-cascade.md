# Pattern — selection cascade

## Problem

Before the app is usable the user must choose a context in several steps — pick an *account*, then a *site*
inside it (or any A → B chain). Most users have exactly one option at some step; asking them to tap a one-item
list is noise. Some users have none, and need a clear dead end instead of an empty list. The server's answer
can change while the user stands on a step (an option disappears, the session dies).

## Shape

```
:selection-ui-api   SelectionFeatureApi, SelectionRoutes
                      FIRST_STEP = "selection/first?prefer={prefer}"   optional query arg (a hint, see below)
                      SECOND_STEP = "selection/second"
                      BLOCKED = "selection/blocked/{reason}"
:selection-ui       SelectionFeatureImpl(doneRoute: String?, signInRoute: String?)   host seams
                      FirstStepViewModel / SecondStepViewModel / BlockedScreen(reason)
:selection          SelectionInteractor
                      suspend fun fetch(mode: FetchMode): FetchOutcome       // ENTRY | RELOAD
                      suspend fun select(firstId, secondId): SelectOutcome
                    SecondStepScope / SecondStepScopeKey (Scopes handoff)
```

Outcomes: `FetchOutcome = Resolved | SelectionRequired(options) | NothingAvailable | SessionExpired |
ConnectionError`; `SelectOutcome = Success | OptionGone | SessionExpired | ConnectionError`.

## Rules

- A step with exactly one option SHOULD be skipped automatically: the VM opens it without rendering a list.
  A step with one option whose own child has one option SHOULD submit straight away.
- Auto-skip SHOULD be a **one-shot entry rule** per VM (`private var autoSkipEnabled = true`): it is spent when
  the list is shown once, on any reload, and after a failed auto-submit. Otherwise a server that keeps offering an
  unavailable single option spins fetch → submit → fetch without user input.
- A skipped step SHOULD navigate with `replaceFlowRoot = true` (`popUpTo(<step>) { inclusive = true }`): the user
  never saw it, so Back must not return to it. When a later step has to restart the flow, the feature impl SHOULD
  re-root with `popUpTo(navController.graph.id) { inclusive = true }` and `launchSingleTop = true`.
- Dead ends ("nothing available", "account blocked") SHOULD be **one blocking screen parameterised by a reason
  enum** (`BlockedScreen(reason)` via a `blockingScreen(route, reason)` factory in the impl) with a sign-out or
  "pick another" action, not N near-identical screens.
- The hand-off from step 1 to step 2 SHOULD go through a `Scopes` key declared in the domain module; the
  consumer VM MUST tolerate `null` (process death) by bouncing back to step 1, and closes the scope in
  `onCleared()` (KERNEL-10).
- A **resume reload never moves the user**: on a return from background the VM re-reads with a list-only mode
  (`FetchMode.RELOAD`) that commits nothing, shows the list even if it now has one option, and drops the reload
  while a request is in flight. Only the entry fetch may auto-navigate.
- The first `ON_RESUME` after creation SHOULD be ignored (`entryResumeConsumed`), because the `init` fetch is
  already running.
- An optional "prefer this option" hint (query arg) SHOULD be spent on the first list seen; if the preferred
  option is gone, the normal cascade takes over.
- `SessionExpired` is swallowed (DATA-18): the host navigates to sign-in.
- Navigation decisions reached before the screen wires its callbacks go through the pending-navigation flush
  (`specs/50-presentation.md`).

## Pitfalls

- Treating auto-skip as permanent: an app switch on the list re-enters the cascade and "jumps" the user forward.
- Committing a choice on reload (a reload that selects resets server-side state the user did not ask to change).
- A Back press from step 2 returning to an auto-skipped step 1 that then auto-skips again — a Back loop.
- Separate screens per dead-end reason that drift apart in copy and actions.

## Test checklist

- [ ] single option at step 1 → no list rendered, navigates with `replaceFlowRoot = true`
- [ ] single option + single child → submits directly; success → done route
- [ ] failed auto-submit → list rendered on re-fetch; next attempt only on tap
- [ ] resume reload with one option → list shown, **no** navigation (contrast with entry fetch)
- [ ] resume while a request is in flight → no second request
- [ ] empty list → blocked screen with the right reason
- [ ] step 2 with an empty `Scopes` slot → bounces back to step 1
- [ ] preferred hint honoured once; ignored when the option is missing
- [ ] `SessionExpired` → no state change, no navigation
