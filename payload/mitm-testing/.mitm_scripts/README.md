# .mitm_scripts

Committed, shared library of **mitmproxy** scripts for developer / QA testing. Each one mocks or modifies
one specific server response, so the app's handling of it (5xx, 401, malformed bodies, latency, dropped
connections) can be exercised on the emulator with a **debug** build.

The full rules for writing these live in the **`mitmproxy-testing`** skill
(`.claude/skills/mitmproxy-testing/SKILL.md`); running a whole verification pass is the on-demand
**`feature-verification`** skill.

## Layout and naming

One subdirectory per HTTP path; the path's `/` become `_` (leading `/` dropped):

```
.mitm_scripts/<path_slug>/<HTTP_METHOD>_<CODE>_<scenario-kebab>.py
.mitm_scripts/<path_slug>/<HTTP_METHOD>_<CODE>_<scenario-kebab>.md   # companion doc (required)
```

- `<path_slug>` — `/v1/auth/login` → `v1_auth_login`. A path **parameter** contributes its name without the
  braces: `/v1/items/{itemId}` → `v1_items_itemId`, which sorts next to the collection's own `v1_items`.
- `<HTTP_METHOD>` — `GET`, `POST`, …
- `<CODE>` — the mocked HTTP status. A dropped connection has no code: when the app treats any non-2xx like a
  dead connection, prefer a status (`GET_503_server-unreachable`) so the name stays in the convention.
- `<scenario-kebab>` — short description of what is tested.

The shipped example: `v1_items/GET_500_example-server-error.{py,md}` — the canonical template.

## `_harness/` — the one exception to "one scenario per file"

`_harness/fake_backend.py` answers **several** endpoints (an example contract: health, login, refresh,
logout, an item list / detail and one idempotent write), which is what lets a test reach a signed-in state
with no credentials and no server. Load a scenario first and the harness second — the harness only answers
what the scenario left alone:

```bash
mitmdump --set flow_detail=1 --set connection_strategy=lazy \
         -s .mitm_scripts/v1_items/GET_500_example-server-error.py \
         -s .mitm_scripts/_harness/fake_backend.py
```

See `_harness/README.md`. `bash tools/drive.sh mitm <scenario.py …>` does the same and logs to `evidence/`.

## Running a script

Prerequisite: emulator on a **debug** build, proxy set, mitmproxy CA installed (one-time — see the
`feature-verification` skill, or `bash tools/drive.sh help`).

```bash
mitmweb -s .mitm_scripts/<path_slug>/<file>.py
```

mitmproxy hot-reloads a script on save. Combine scenarios with several `-s` flags (keep their matches
disjoint).

## Conventions (summary)

- Precise matching (exact method + path); short-circuit by default (the real server is not called).
- Mock bodies mirror the real DTO / envelope contract — or break it on purpose for a malformed-body test.
- **No real secrets or personal data** in committed bodies — synthetic data only (`example.test`).
- One scenario per file, always with its `.md`.
