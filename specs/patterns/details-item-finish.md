# Pattern — details → item card → finish

## Problem

A unit of work has a details screen (header + list of items), an item card where the user edits or confirms one
item, and a final summary with a "finish" action that closes the whole unit on the server. Each screen must open
fast, survive process death, reflect the user's own changes immediately, and never close the unit twice or
against a state the user did not see. Colleagues may change the same unit concurrently.

## Shape

```
:work-ui-api   WorkRoutes: DETAILS = "work/{workId}", ITEM = "work/{workId}/item/{itemId}",
               SUMMARY = "work/{workId}/summary"           ids only — never payloads
:work-ui       DetailsViewModel (flow root), ItemViewModel, SummaryViewModel, WorkFeatureImpl(seams…)
:work          WorkInteractor
                 suspend fun load(workId, isEntry: Boolean): WorkFetchResult<WorkDetails>
                 suspend fun confirmItem(workId, attempt: ConfirmAttempt): ConfirmOutcome
                 suspend fun loadSummary(workId): WorkFetchResult<WorkSummary>
                 suspend fun finish(workId, attempt: FinishAttempt): FinishOutcome
                 fun newRequestId(): String
               interface WorkSnapshotStore { peek(workId); put(workId, details); applyConfirmation(workId, c); clear(workId) }
               DefaultWorkSnapshotStore @Inject constructor(scopes: Scopes)   keyed "work-snapshot:<workId>"
               data class FinishAttempt(val requestId: String, val versionToken: String)
               WorkService (sealed WorkFetchResult<out T> with write rungs RetryLater, StatusChanged, Rejected…)
```

## Rules

- **One read backs the flow.** The details read SHOULD return everything the item cards need; the details VM
  `put`s it into the snapshot store, and is the store entry's owner: `onCleared() { store.clear(workId) }`.
- The item card SHOULD **open without a request**: it `peek`s the snapshot; only on `null` (process death,
  deep link) does it fall back to one read (DATA-41).
- A confirmation's response SHOULD be **written back** (`applyConfirmation`) so the details screen repaints
  instantly on return; the details screen still re-reads on resume and that read is authoritative (DATA-47).
- `isEntry` SHOULD distinguish the first read of a visit from later ones where the meaning of a state differs
  (e.g. "already finished" on entry = a recap the user may look at; on a re-read = someone else finished it → exit).
- A failed **re-read keeps the content** and raises a one-slot notice; a full-body failure is shown only when
  there is nothing to show.
- Writes SHOULD follow DATA-50…54: the attempt (request id + body) is created when the user commits, stored in
  VM state, and resent unchanged; `RetryLater` → bounded backoff with the same attempt; definitive rungs drop it.
- The finish's optimistic-lock token SHOULD come from the **summary read the user confirmed**, stored in the
  attempt (DATA-52), never re-read before a retry.
- **Status changed on a write ≠ on a read** (DATA-53): on the finish, the interactor re-reads and returns
  `Resynced(freshSummary)` (show the new numbers, let the user confirm again with a new attempt) or
  `Unavailable`; on a read, "status changed" is plain `Unavailable` → exit flow (`exit-flow.md`).
- Server gates on the finish (`Rejected(items)`, "required data missing") SHOULD render the list the server named,
  with no retry button — retrying unchanged cannot succeed.
- Leaving after a successful finish SHOULD use the exit flow: pop the feature root and set a result flag on the
  landing entry.

## Pitfalls

- Passing the read model through route args or a global singleton without an owner (stale, leaky, lost on death).
- Minting a new request id on retry, or re-reading the version before a retry → double close or a commit against
  numbers the user never saw.
- Folding a write-time conflict into "gone" → users ejected from work that is still theirs.
- The details VM trusting the written-back snapshot and skipping its re-read.
- Clearing the store from a child VM (the parent still needs it).

## Test checklist

- [ ] details read → snapshot `put`; details VM `onCleared` → `clear`
- [ ] item card with a snapshot → **zero** requests on open; without → exactly one read
- [ ] confirm success → store updated → details state reflects it before its re-read completes
- [ ] re-read failure with content → content kept + notice; without content → failure body
- [ ] transient finish failure → retry sends the **same** request id and version token (recorded by the fake)
- [ ] `RetryLater` → resend after backoff, stops after the maximum, surfaces as transient
- [ ] status changed on finish → re-read → `Resynced` (new numbers) / `Unavailable` (exit)
- [ ] status changed on a read → exit flow
- [ ] double tap on finish → one attempt in flight
- [ ] service tests: every rung of the write ladder over MockWebServer, request body carries the id and token
