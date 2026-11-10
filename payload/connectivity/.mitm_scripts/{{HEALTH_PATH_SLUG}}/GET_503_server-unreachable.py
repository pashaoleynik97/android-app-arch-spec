"""
Scenario: the backend's health check answers 503 — the device is online but the server is not usable.
Endpoint: GET /{{HEALTH_PATH}}  (relative to the base URL — see the sibling .md)
Run:      mitmdump --set flow_detail=1 --set connection_strategy=lazy \
                   -s .mitm_scripts/{{HEALTH_PATH_SLUG}}/GET_503_server-unreachable.py \
                   -s .mitm_scripts/_harness/fake_backend.py

Precise match, short-circuit in the request hook. Toggle FAILING to False (save = hot reload) to watch recovery.
"""
import json
import re
import time

from mitmproxy import http

METHOD = "GET"
HEALTH_PATH = "{{HEALTH_PATH}}"
PATH = re.compile(r"^(/.*)?/" + re.escape(HEALTH_PATH.strip("/")) + r"$")
STATUS = 503
FAILING = True

BODY = json.dumps({"status": "unavailable"}).encode()


def _matches(flow: http.HTTPFlow) -> bool:
    return flow.request.method == METHOD and PATH.match(flow.request.path.split("?", 1)[0]) is not None


def _log(what: str) -> None:
    print(f"[GET_503_server-unreachable {time.strftime('%H:%M:%S')}] {what}", flush=True)


def request(flow: http.HTTPFlow) -> None:
    if flow.response is not None or not FAILING:
        return
    if _matches(flow):
        _log(f"{METHOD} {flow.request.path} -> {STATUS}")
        flow.response = http.Response.make(STATUS, BODY, {"Content-Type": "application/json"})
