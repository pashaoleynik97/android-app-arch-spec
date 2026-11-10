"""
Scenario: the item list answers 500, to exercise the list screen's server-error branch.
Endpoint: GET /v1/items  (example contract — see the sibling .md)
Run:      mitmdump --set flow_detail=1 --set connection_strategy=lazy \
                   -s .mitm_scripts/v1_items/GET_500_example-server-error.py \
                   -s .mitm_scripts/_harness/fake_backend.py

Canonical template: precise match (exact method + exact path, query ignored) and short-circuit (the real
server is never called). Copy it for a new scenario and change METHOD / PATH / STATUS / BODY.
"""
import json
import re
import time

from mitmproxy import http

METHOD = "GET"
# Exact path, anchored; a regex only to allow a base-path prefix (and, elsewhere, path parameters such as
# r"/v1/items/[^/]+"). Never a loose `in` / `contains`: it would catch calls the scenario is not about.
PATH = re.compile(r"^(/.*)?/v1/items$")
STATUS = 500

# Mirrors the error envelope of the example contract (`_error` in _harness/fake_backend.py). A body the
# app cannot parse as its error envelope would test the malformed-body branch instead of this one.
BODY = json.dumps({
    "error": {"error": "INTERNAL", "message": "Internal server error", "details": []},
    "timestamp": "2026-01-01T00:00:00.000Z",
}).encode()


def _matches(flow: http.HTTPFlow) -> bool:
    return flow.request.method == METHOD and PATH.match(flow.request.path.split("?", 1)[0]) is not None


def _log(what: str) -> None:
    # A short-circuited flow never reaches the harness or upstream; this line is the only evidence the
    # app asked, so it carries a wall-clock time like the harness's own log.
    print(f"[GET_500_example-server-error {time.strftime('%H:%M:%S')}] {what}", flush=True)


def request(flow: http.HTTPFlow) -> None:
    if flow.response is not None:  # an earlier script already answered
        return
    if _matches(flow):
        _log(f"{METHOD} {flow.request.path} -> {STATUS}")
        flow.response = http.Response.make(STATUS, BODY, {"Content-Type": "application/json"})
