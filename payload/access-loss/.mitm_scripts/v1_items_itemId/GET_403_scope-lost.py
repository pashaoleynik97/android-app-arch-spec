"""
Scenario: reading an item is refused because the user lost the scope their session is bound to.
Endpoint: GET /v1/items/{itemId}  (example contract — see the sibling .md)
Run:      mitmdump --set flow_detail=1 --set connection_strategy=lazy \
                   -s .mitm_scripts/v1_items_itemId/GET_403_scope-lost.py \
                   -s .mitm_scripts/_harness/fake_backend.py

Precise match with a path parameter, short-circuit in the request hook.
"""
import json
import re
import time

from mitmproxy import http

METHOD = "GET"
PATH = re.compile(r"^(/.*)?/v1/items/[^/]+$")
STATUS = 403

# The scope-level code of the example contract (AccessLossConfig.scopeLevelCodes).
BODY = json.dumps({
    "error": {"error": "SCOPE_ACCESS_REVOKED", "message": "Access to this workspace was revoked", "details": []},
    "timestamp": "2026-01-01T00:00:00.000Z",
}).encode()


def _matches(flow: http.HTTPFlow) -> bool:
    return flow.request.method == METHOD and PATH.match(flow.request.path.split("?", 1)[0]) is not None


def _log(what: str) -> None:
    print(f"[GET_403_scope-lost {time.strftime('%H:%M:%S')}] {what}", flush=True)


def request(flow: http.HTTPFlow) -> None:
    if flow.response is not None:
        return
    if _matches(flow):
        _log(f"{METHOD} {flow.request.path} -> {STATUS} SCOPE_ACCESS_REVOKED")
        flow.response = http.Response.make(STATUS, BODY, {"Content-Type": "application/json"})
