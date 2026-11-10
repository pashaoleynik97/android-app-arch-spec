# <TASK-ID> — verification (<YYYY-MM-DD>)

Environment: <device / emulator, API level, screen>, build <variant> from <working tree | commit>; scenario
files used <(new ones marked)>; setup path <…>. Evidence in `evidence/`.

## Unit / build
<commands> → <counts>

## Results
| # | Case | Expected | Observed / evidence | Result |
|---|---|---|---|---|
| 1 | | | <request-log timestamp, UI-dump value, screenshot file name> | PASS / FAIL |

Every PASS cites evidence, never "looks right". A "nothing happened" case is proven with a contrast pair and
observed over a window long enough for the call to have happened.

## Findings
None | 1. <finding> — regression / UX / pre-existing / out of scope

## Not verified
<What could not be reached and why.>

## Environment state left
<proxy settings, running processes, signed-in state, installed certificates> — undo: `<command>`
