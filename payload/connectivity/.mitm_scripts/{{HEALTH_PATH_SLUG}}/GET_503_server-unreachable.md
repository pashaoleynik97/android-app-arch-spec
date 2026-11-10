# GET 503 — server-unreachable

- **Endpoint:** `GET <base URL>{{HEALTH_PATH}}` — the connectivity component's health probe. The folder is the
  health path's slug (MITM-02), rendered from the installer answer.
- **Match:** exact method + path (any base-path prefix), query ignored
- **Behavior:** short-circuit while `FAILING = True`; with `False` the harness answers 200
- **Mocked response:** `503`, body `{"status":"unavailable"}`
- **How to run it:** start mitmdump with this scenario first and the harness second; launch the app (or toggle
  airplane mode off/on to trigger a re-probe).
- **Expected app behavior:** the "No connection to the server" banner appears once, with retry and close.
  Tapping retry sends exactly one new probe and spins briefly; the banner stays. Set `FAILING = False`, save,
  tap retry: the banner turns into "Connection restored", which disappears by itself after a few seconds.
- **What would be a bug:** probes on a timer with no user action or link change (no polling by design); the
  banner reappearing after being closed while still unreachable; a restored banner when the state only went
  back to unknown; any auth request caused by the probe (it is unauthenticated).
- **Run:**
  `mitmdump --set flow_detail=1 --set connection_strategy=lazy -s .mitm_scripts/{{HEALTH_PATH_SLUG}}/GET_503_server-unreachable.py -s .mitm_scripts/_harness/fake_backend.py`
- **Related:** `references/connectivity.md` (CONN rules).
