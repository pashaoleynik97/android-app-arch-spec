# GET 500 — example-server-error

> Example scenario for the example contract. Keep it as the template; delete it once your own endpoints
> have scenarios.

- **Endpoint:** `GET /v1/items`
- **Match:** exact method + path (any base-path prefix allowed), query ignored, no body condition
- **Behavior:** short-circuit (`request` hook — the real server and the harness are **not** called)
- **Mocked response:** `500`, `Content-Type: application/json`, body
  `{"error":{"error":"INTERNAL","message":"Internal server error","details":[]},"timestamp":"…"}`
  (the error envelope of the example contract; adapt it to your network module's DTOs)
- **How to run it:**
  1. Start mitmdump with this scenario **first** and the harness second (command below).
  2. Sign in (the harness accepts `user@example.test` / `password`).
  3. Open the list screen, or pull to refresh on it.
- **Expected app behavior:** the list shows its generic error state with a retry action; no crash; signing
  in still worked (the harness served login), so only the list is affected. Retry sends exactly one new
  `GET /v1/items`.
- **What would be a bug:** a `POST /v1/auth/refresh` after the 500 (a 5xx is not an auth failure); an
  empty list presented as "nothing here" instead of an error; a retry loop with no user action.
- **Run:**
  `mitmdump --set flow_detail=1 --set connection_strategy=lazy -s .mitm_scripts/v1_items/GET_500_example-server-error.py -s .mitm_scripts/_harness/fake_backend.py`
- **Related:** `_harness/fake_backend.py` (`GET /v1/items`), the `mitmproxy-testing` skill.
