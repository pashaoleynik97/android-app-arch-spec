---
paths:
  - "**/domain/**/*.kt"
---

# Domain code

Owner: the data-and-errors reference of **application-architecture** (and the network reference when that
component is installed). The rules most often broken here:

- No Compose, ViewModel, lifecycle or navigation types; no dependency on any UI module (DATA-01).
- The interactor is the only type a ViewModel sees; it returns feature-level sealed outcomes, never exceptions,
  `kotlin.Result`, HTTP types or DTOs (DATA-02, DATA-03).
- Services classify into their own sealed result; anything ambiguous is transient (DATA-10, DATA-12).
- Catch only `IOException` and `SerializationException`; cancellation always propagates (DATA-13).
- Every suspend API is main-safe through its injected dispatcher (DATA-08).
- Ids, time, dispatchers, scopes and tuning are injected seams; single-value configs are data classes (DATA-06).
- Unknown wire enum values degrade instead of throwing (DATA-21); stores never throw on corrupt data (DATA-46).
- Non-idempotent writes reuse one request id and body per attempt until a definitive answer (DATA-50).
