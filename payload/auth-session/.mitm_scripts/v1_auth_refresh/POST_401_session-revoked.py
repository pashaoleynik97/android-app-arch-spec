"""
Scenario: every token refresh is refused as a revoked session ("signed in on another device").
Endpoint: POST /v1/auth/refresh  (example contract — see the sibling .md)
Run:      mitmdump --set flow_detail=1 --set connection_strategy=lazy \
                   -s .mitm_scripts/v1_auth_refresh/POST_401_session-revoked.py \
                   -s .mitm_scripts/_harness/fake_backend.py

Precise match (method + path, query ignored), short-circuit in the request hook: the real server is never asked.
Set the harness knob EXPIRES_IN to ~60 so the access token lapses quickly and the app has to refresh.
"""
import json
import re
import time

from mitmproxy import http

METHOD = "POST"
PATH = re.compile(r"^(/.*)?/v1/auth/refresh$")
STATUS = 401

# The error envelope of the example contract, with the precise reason in `details.reason` that the session
# module maps to SessionEndReason.NEW_LOGIN (lower-case constant name).
BODY = json.dumps({
    "error": {"error": "SESSION_REVOKED", "message": "Session revoked", "details": {"reason": "new_login"}},
    "timestamp": "2026-01-01T00:00:00.000Z",
}).encode()


def _matches(flow: http.HTTPFlow) -> bool:
    return flow.request.method == METHOD and PATH.match(flow.request.path.split("?", 1)[0]) is not None


def _log(what: str) -> None:
    print(f"[POST_401_session-revoked {time.strftime('%H:%M:%S')}] {what}", flush=True)


def request(flow: http.HTTPFlow) -> None:
    if flow.response is not None:
        return
    if _matches(flow):
        _log(f"{METHOD} {flow.request.path} -> {STATUS} SESSION_REVOKED (new_login)")
        flow.response = http.Response.make(STATUS, BODY, {"Content-Type": "application/json"})
