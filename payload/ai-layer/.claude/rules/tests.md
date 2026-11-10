---
paths:
  - "**/src/test/**/*.kt"
---

# JVM unit tests

Owner: the testing reference of **application-architecture**. The rules most often broken here:

- No mocking library: write a `Fake<Interface>` (scriptable, recording) next to the tests that use it; shared
  kernel doubles (dispatcher rule, fake time source, recording reporter) come from `core-testing` (TEST-02,
  TEST-04, TEST-10).
- Method names are backtick sentences stating the behaviour (TEST-30).
- Time is virtual: `runTest`, `advanceTimeBy`, `advanceUntilIdle`; never `Thread.sleep` (TEST-23). Endless
  loops run on `backgroundScope` (TEST-22).
- In-flight states are tested with gates (`CompletableDeferred`, held requests) and resolved out of order where
  order matters (TEST-14, TEST-21).
- Prove a "nothing happened" with a contrast pair, after advancing past the window (TEST-40, TEST-41).
- Assert on logging with the recording-reporter rule from `core-testing`; a hand-rolled reporter is removed
  in `@After` (KERNEL-07).
- Construct the class under test directly with fakes; no Hilt test components (DI-16).
