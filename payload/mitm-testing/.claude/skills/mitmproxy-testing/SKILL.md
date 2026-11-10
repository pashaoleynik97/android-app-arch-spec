---
name: mitmproxy-testing
description: >
  Developer testing with mitmproxy — mock / inject / modify server responses to exercise how a feature
  handles them (error codes, edge payloads, latency, connection faults) on the Android emulator with a
  debug build. Use OPTIONALLY when building or changing any feature that handles server responses and you
  want to test response scenarios, or whenever asked to configure mitmproxy to test app behaviour for a
  specific endpoint (e.g. "test POST /v1/auth/login returning 500"). Defines the requirements checklist,
  the committed `.mitm_scripts/<path>/<METHOD>_<CODE>_<scenario>.py` + `.md` convention, the canonical
  precise-match + short-circuit template, and the `_harness/fake_backend.py` composition rule.
---

# mitmproxy developer testing

How to drive the app's traffic through **mitmproxy** and return fake or modified server responses, so a
feature's reaction to what the real backend rarely produces (5xx, 401, malformed bodies, latency, dropped
connections) can be checked deterministically. Rules cite `specs/recommended/mitm-testing.md` (`MITM-*`).

This skill is **optional**: reach for it when working on a feature that consumes server responses, or when
explicitly asked to build a mitmproxy test for an endpoint. It is the source of truth for `.mitm_scripts/`;
keep it and the examples current as endpoints evolve.

Interception needs no app code: OkHttp honours the system proxy, and the committed debug-only
`app/src/debug/res/xml/network_security_config.xml` trusts user CAs. Only **debug** builds are
interceptable; release builds are locked by design (MITM-01).

---

## 1. When this applies

- Building or changing a feature that branches on server responses (error handling, empty states,
  retry / refresh, parsing) and you want to test those branches deterministically.
- A developer asks, e.g. *"configure mitmproxy to test how the app handles a 500 on `POST /v1/auth/login`"*.

If the request is vague, **collect the requirements first** (§2) before writing anything.

## 2. Collect the test requirements

Ask for anything missing, and pull the rest from the code (the Retrofit `*Api` interface, the DTOs, the
interactor that consumes the response):

1. **Endpoint path** — e.g. `/v1/auth/login` (from the Retrofit interface).
2. **HTTP method**.
3. **Scenario** — short kebab description, e.g. `test-internal-server-error-handling`.
4. **Mocked response** — status, body, headers. **Match the real serialization contract** (§5).
5. **Match precision** — exact method + exact path by default. Add a body / header condition only if the
   scenario needs it.
6. **Behaviour** — **short-circuit** (fabricate; real server never called — the default) or **edit the
   real response** (§6).
7. **Faults** (optional) — latency, timeout, connection reset.
8. **Expected app behaviour** and **what would be a bug** — a falsifiable negative.

## 3. Where the script goes (MITM-02)

```
.mitm_scripts/<path_slug>/<HTTP_METHOD>_<CODE>_<scenario-kebab>.py
.mitm_scripts/<path_slug>/<HTTP_METHOD>_<CODE>_<scenario-kebab>.md   # companion (required)
```

- **`<path_slug>`** = path without the leading `/`, remaining `/` → `_`: `/v1/auth/login` → `v1_auth_login`.
  A path parameter contributes its **name without braces**: `/v1/items/{itemId}` → `v1_items_itemId`.
- **`<HTTP_METHOD>`** uppercase; **`<CODE>`** the mocked status; **`<scenario-kebab>`** short and specific.
- A dropped connection has no status. When the app treats any non-2xx like a dead connection, mock a
  status instead (`GET_503_server-unreachable`) and say why in the `.md`.

Every `.py` **must** have a sibling `.md` (§7). One scenario per file — never multiplex.

**The one exception: `_harness/`** (MITM-05). `_harness/fake_backend.py` fakes a whole signed-in session
(example contract: health, login, single-use-refresh-token refresh, logout, a paginated item list, detail
and one idempotent write), with knobs for token lifetime, latency, forced failures and pool sizes. Its
state is mutable, so a write is visible on the next read. **Compose, don't fork:** load the scenario
**first** and the harness **last** — the harness answers only what the scenario left alone:

```bash
mitmdump --set flow_detail=1 --set connection_strategy=lazy \
         -s .mitm_scripts/v1_items/GET_500_example-server-error.py \
         -s .mitm_scripts/_harness/fake_backend.py
```

Adapting the harness to your API: its docstring lists every handled route; change `_ok` / `_error` (the
envelope), the `ROUTES` table and the handlers; keep it **as strict as the real API** (MITM-06) and extend
its `--selftest` with every handler you add (`python3 -I .mitm_scripts/_harness/fake_backend.py --selftest`).

## 4. Canonical script template (precise match + short-circuit) (MITM-03)

```python
"""
Scenario: <one line>. See the sibling .md for full details.
Endpoint: POST /v1/auth/login
Run:      mitmdump --set flow_detail=1 -s .mitm_scripts/v1_auth_login/POST_500_<scenario>.py \
                   -s .mitm_scripts/_harness/fake_backend.py
"""
import json, re, time
from mitmproxy import http

METHOD = "POST"
PATH = re.compile(r"^(/.*)?/v1/auth/login$")      # exact path; a regex only for a base path / path params
STATUS = 500
BODY = json.dumps({"error": {"error": "INTERNAL", "message": "Internal server error", "details": []},
                   "timestamp": "2026-01-01T00:00:00.000Z"}).encode()

def _matches(flow: http.HTTPFlow) -> bool:
    # Exact method + exact path, query ignored. Never a loose `in` / contains.
    return flow.request.method == METHOD and PATH.match(flow.request.path.split("?", 1)[0]) is not None

def _log(what: str) -> None:
    # A short-circuited flow never reaches upstream: this line is the only evidence the app asked.
    print(f"[POST_500_<scenario> {time.strftime('%H:%M:%S')}] {what}", flush=True)

def request(flow: http.HTTPFlow) -> None:
    if flow.response is not None:          # an earlier script already answered
        return
    if _matches(flow):
        _log(f"{METHOD} {flow.request.path} -> {STATUS}")
        flow.response = http.Response.make(STATUS, BODY, {"Content-Type": "application/json"})
```

The committed example `.mitm_scripts/v1_items/GET_500_example-server-error.py` is this template, ready to
copy. Adapt `METHOD` / `PATH` / `STATUS` / `BODY`; keep the match precise and the timestamped log line.

**Stateful variants.** A module-level counter makes "first call passes, later calls fail" (or the
reverse) expressible: `_calls = 0` … `global _calls; _calls += 1; if _calls == 1: return`. Document the
sequence in the `.md`. mitmproxy hot-reloads on save, which also resets module state.

## 5. Make the mocked body match the contract (MITM-04)

The app deserializes responses into DTOs; a mock that ignores the real shape tests nothing (or fails to
parse and masks the branch you meant to hit). **Read the DTOs and mirror the envelope.**

Example — with the example contract's error envelope
```json
{ "error": { "error": "<CODE>", "message": "...", "details": [] }, "timestamp": "..." }
```
a realistic 500 is `{"error":{"error":"INTERNAL","message":"Internal server error","details":[]}}` — not a
naive `{"error":"Internal server error"}`, which the error classifier would not read as an envelope and
which therefore exercises the *malformed-body* branch instead.

For a **malformed-body** scenario, do the opposite on purpose — bytes that don't satisfy the DTO — and say
so in the `.md`.

## 6. Short-circuit vs edit-the-real-response

- **`request` hook (short-circuit) — default.** Setting `flow.response` before the request leaves
  fabricates the answer; the real server is never called. Best for forced errors, deterministic bodies and
  backends that do not exist yet.
- **`response` hook (edit real).** Runs after the server replied; use it when the scenario needs the real
  payload with a small change:
  ```python
  def response(flow: http.HTTPFlow) -> None:
      if _matches(flow):
          flow.response.status_code = 500
          flow.response.set_text('{"error":{"error":"INTERNAL","message":"Internal server error","details":[]}}')
  ```

Rule of thumb: **`request` = fabricate without the server; `response` = tweak what the server returned.**

### Faults
- **Latency:** `time.sleep(SECONDS)` in `request` before answering (or the harness's `*_LATENCY` knobs).
- **Connection reset:** `flow.kill()` — no status to name the file after; prefer a status when the app
  treats both alike (§3).

## 7. Companion `.md` (required, one per script)

```markdown
# <METHOD> <CODE> — <scenario>

- **Endpoint:** `POST /v1/auth/login`
- **Match:** exact method + path (regex for any id), query ignored [, body condition]
- **Behavior:** short-circuit (real server not called) | pass-through first call, then … | edit real response
- **Mocked response:** `<code>`, body `{…}` (mirrors the envelope in `<module>/…/dto/<X>Dtos.kt`)
- **How to run it:** numbered steps if the trigger needs UI actions or waiting
- **Expected app behavior:** what the user sees; what must / must not be requested
- **What would be a bug:** a falsifiable negative, e.g. "a refresh request right after the 500"
- **Run:** `mitmdump --set flow_detail=1 -s <this>.py -s .mitm_scripts/_harness/fake_backend.py`
- **Related:** task / PR, sibling scenarios, docs section
```

Longer `.md`s may add `## Why <design choice>` sections (e.g. why a status instead of a reset).

## 8. Running

Prerequisite (one-time, see `bash tools/drive.sh help` or the `feature-verification` skill): emulator on a
debug build, proxy set (`bash tools/drive.sh proxy-on`), mitmproxy CA installed as a user CA.

```bash
bash tools/drive.sh mitm .mitm_scripts/<path_slug>/<file>.py     # + harness, logs to evidence/mitm.log
# or interactively:
mitmweb --set connection_strategy=lazy -s .mitm_scripts/<path_slug>/<file>.py
```

- `--set flow_detail=1` is mandatory for evidence: with `0`, a short-circuiting scenario is invisible.
- `--set connection_strategy=lazy`: flows answered locally never dial the real host (MITM-07).
- mitmproxy **hot-reloads** scripts on save. Combine scenarios with several `-s` (disjoint matches).
- Undo: `bash tools/drive.sh proxy-off && bash tools/drive.sh mitm-stop`.

## 9. Verifying a whole feature

A full evidence-based pass on the emulator is the **`feature-verification`** skill. It is **on-demand
only**: use it when the developer explicitly asks to verify / QA a task on a device.

## 10. Guardrails

- **Precise matching** — exact method + path; never a loose `contains` in a committed script.
- **Short-circuit by default**; the `response` hook only when the scenario truly needs the real payload.
- **No real secrets or personal data** in committed bodies — synthetic tokens / ids / `example.test`
  emails. Never paste a token captured from a real environment.
- **One scenario per file**, always with its `.md`.
- **Keep bodies aligned with the DTOs** — update scenarios (and the harness) when a contract changes.
- **Debug builds only** — never add the user-CA trust to `src/main` or a release variant (MITM-01).
