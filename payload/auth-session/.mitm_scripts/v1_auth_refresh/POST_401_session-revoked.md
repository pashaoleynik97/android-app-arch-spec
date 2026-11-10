# POST 401 — session-revoked

- **Endpoint:** `POST /v1/auth/refresh` (example contract of the auth-session component)
- **Match:** exact method + path (any base-path prefix allowed), query ignored, no body condition
- **Behavior:** short-circuit (`request` hook) — the harness and the real server are not called for refresh
- **Mocked response:** `401`, body
  `{"error":{"error":"SESSION_REVOKED","message":"Session revoked","details":{"reason":"new_login"}},"timestamp":"…"}`
- **How to run it:**
  1. In `_harness/fake_backend.py` set `EXPIRES_IN = 60` (saving hot-reloads the harness).
  2. Start mitmdump with this scenario **first** and the harness second (command below).
  3. Sign in (`user@example.test` / `password`), wait a minute, then tap anything that calls the API (or just
     touch the screen past the keep-alive checkpoint).
- **Expected app behavior:** exactly one `POST /v1/auth/refresh` per expiry (single-flight, even if several
  requests 401 at once); the app lands on the sign-in screen with the "You signed in on another device" notice;
  the back button does not return into the app; the stored token is gone (a cold start shows sign-in).
- **What would be a bug:** two refreshes for one expiry; a retry loop of refreshes; staying on the previous
  screen; the generic "session ended" notice instead of the precise one; a crash on the `details` object.
- **Run:**
  `mitmdump --set flow_detail=1 --set connection_strategy=lazy -s .mitm_scripts/v1_auth_refresh/POST_401_session-revoked.py -s .mitm_scripts/_harness/fake_backend.py`
- **Related:** `references/auth-session.md` (AUTH rules), `_harness/fake_backend.py` (`POST /v1/auth/refresh`).
