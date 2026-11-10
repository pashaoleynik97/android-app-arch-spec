# `_harness/` — multi-endpoint fixtures

Everything in the path directories (`v1_items/`, …) is **one scenario per file**, matching exactly one
endpoint. This directory is the deliberate exception: fixtures here answer *several* endpoints so a test can
reach a given app state at all.

| File | What it gives you |
|---|---|
| `fake_backend.py` | A whole signed-in session — health, login, refresh (single-use refresh tokens), logout, a paginated item list, item detail and an idempotent write — with no credentials and no server. Knobs for token lifetime, latency, forced failures and pool size. |

The endpoints and envelope are an **example contract**: adapt them to your API (the module docstring says
where). Whatever you change, keep the design rules:

1. **Compose, don't fork.** Scenario first, harness last; the harness starts every hook with
   `if flow.response is not None: return`, so the scenario stays the single source of truth for its endpoint.
2. **Docstring = contract.** Every handled `METHOD path -> behaviour` and every knob is listed at the top.
3. **Knobs at the top** (hot-reloaded): the token lifetime the app is told (a short one turns an hour of
   idle into a minute), per-endpoint latency, forced failure codes taken from a table of every real refusal,
   pool sizes chosen to exercise paging.
4. **As strict as the real API.** Tokens minted here are the only ones accepted; refresh tokens are single
   use; unknown fields are refused, not ignored. A lenient fake lets tests pass that should fail.
5. **Mutable in-memory state**, so a write is visible on the next read and a status transition happens
   exactly once.
6. **One server clock** (`_iso`) for every timestamp, so durations never run backwards.
7. **Real request parsing** (JSON that never raises, query parameters) — a wrong field name surfaces as a
   400 here, not as a mystery on the device.
8. **Envelope helpers** (`_ok`, `_error`) — the one place to mirror your DTOs.
9. **A timestamped log line for every handled call**, with loud markers for events tests assert on
   (`*** A REFRESH HAPPENED ***`). The log doubles as the flow list, which makes "absence of a request"
   checkable. Secrets are truncated.
10. **Unhandled → logged and passed through**; other hosts → passed through silently.
11. **Synthetic data only.**

Self-test, no mitmproxy needed (the installer gate runs it):

```bash
python3 -I .mitm_scripts/_harness/fake_backend.py --selftest
```

Extend the self-test whenever you add or change a handler.

```bash
mitmdump --set flow_detail=1 --set connection_strategy=lazy \
         -s .mitm_scripts/<path>/<SCENARIO>.py -s .mitm_scripts/_harness/fake_backend.py
```

`connection_strategy=lazy` stops mitmproxy from dialling the real host for flows answered locally, so this
works before the backend exists. Full emulator workflow: the **`feature-verification`** skill.
