# Plan — <TASK-ID> <subtask>

Status: **<awaiting approval | FINAL — implement as written>**
Context: 00-analysis.md

## Goal
<The user-visible outcome, in one or two sentences.>

## Finding
<What the code does today, with file:line anchors. "No behaviour change needed" is a valid finding.>

## Changes
1. Code — `<module>/src/main/…/<File>.kt`: <what changes>.
2. Tests — `<module>/src/test/…/<File>Test.kt`: new cases `<backtick sentence>`, `<…>`.
3. Test fixtures / scripted responses: <…> (or "none").
4. Docs — `.docs/<page>.md`: <section> (or "none").
5. Skills — `<skill>` § <section / rule ID>: <what changes> (or "none — reason").

## Out of scope
- <…>

## Verification
```
./gradlew :<module>:testDebugUnitTest :<module>-ui:assembleDebug
```
Optional on-demand device run: <scenario> (only if the developer asks).

## Risks
- <…>

## Decisions log (<YYYY-MM-DD>)
<Appended after the approval conversation.>
