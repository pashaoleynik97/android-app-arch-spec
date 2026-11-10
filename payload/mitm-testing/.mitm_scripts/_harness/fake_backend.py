"""
Fake backend — a whole signed-in session with no credentials and no server. A HARNESS, not a scenario.

It deliberately answers several endpoints, which is why it lives in `_harness/` while everything else in
`.mitm_scripts/` is one scenario per file. Use it whenever a test needs the app signed in and holding
data, and the point of the test is what the *app* does — not what a shared environment happens to return.

Compose it with any committed scenario. **The scenario goes first** and wins on the endpoint it owns; this
harness answers only what is left (`if flow.response is not None: return`):

    mitmdump --set flow_detail=1 --set connection_strategy=lazy \
             -s .mitm_scripts/v1_items/GET_500_example-server-error.py \
             -s .mitm_scripts/_harness/fake_backend.py

`connection_strategy=lazy` keeps mitmproxy from dialling the real host for flows answered here, so the
harness works even when the backend does not exist yet (or the base URL is still a placeholder).

THE CONTRACT BELOW IS AN EXAMPLE TO ADAPT. Paths, field names and the envelope mirror the example contract
the `network` / `auth-session` reference code ships. When your API differs, change `_ok` / `_error`, the
route table at the bottom and the handlers — and keep the harness exactly as strict as the real API.

Only requests to the host of BASE_URL are considered; everything else (system traffic on the emulator)
passes through untouched and unlogged. Paths below are relative to BASE_URL's path.

Handled (example contract):
    GET  <HEALTH_PATH>               -> 200 {"status":"ok"}  (no envelope, no auth — a liveness probe)
    POST /v1/auth/login              -> {accessToken, refreshToken, expiresIn, user}. Body {email, password};
                                        a missing field is 400 VALIDATION_ERROR, a wrong pair 401
                                        INVALID_CREDENTIALS
    POST /v1/auth/refresh            -> a ROTATED pair. Refresh tokens are single-use: presenting one twice
                                        is 401 REFRESH_TOKEN_INVALID (and logged loudly) — exactly the case a
                                        client without single-flight refresh trips over
    POST /v1/auth/logout             -> 200; the session's tokens stop working
    GET  /v1/items                   -> paginated list: `page`, `limit` (default 30, max 100), `search`
                                        (case-insensitive, title), `status` (open|done)
    GET  /v1/items/{itemId}          -> one item, or 404 ITEM_NOT_FOUND
    POST /v1/items/{itemId}/complete -> open -> done, exactly once. Requires an `Idempotency-Key` header:
                                        missing = 400; a key seen before replays the stored answer with
                                        `isReplay: true`; a new key on a done item = 409 ITEM_STATUS_CHANGED.
                                        Body {note?}; any other field is refused with 400

Every endpoint except health and login requires `Authorization: Bearer <access>` minted here; an unknown
token is 401 TOKEN_INVALID, an expired one 401 TOKEN_EXPIRED. Anything unhandled on the API host is
logged and passed through to the real host.

Knobs (edit and save — mitmproxy hot-reloads the script, which also resets the in-memory state):
    EXPIRES_IN       the access-token lifetime the app is TOLD and that is enforced. 3600 = a normal session;
                     ~60 makes refresh / keep-alive / idle-logout behaviour observable in a minute.
    ENFORCE_EXPIRY   False = expired tokens keep working (isolate a test from refresh behaviour).
    LIST_LATENCY / DETAIL_LATENCY / WRITE_LATENCY
                     seconds to stall those endpoints, so loaders, paging footers and in-flight buttons last
                     long enough to screenshot (and to background the app mid-request).
    DETAIL_FAILURE   None, or a key of DETAIL_FAILURES: every detail read is refused with it.
    ITEM_POOL_SIZE   74 at the default page size of 30 = three pages (30/30/14): the append trigger fires
                     twice, then `hasNextPage: false` stops it.
    USERS            the accepted credentials (synthetic).

Every handled call prints one timestamped line, so the mitmdump log doubles as the flow list — which is
what makes "no POST /v1/auth/refresh followed that tick" checkable. Secrets are never logged in full.

Self-test (no mitmproxy needed):  python3 -I .mitm_scripts/_harness/fake_backend.py --selftest
"""
from __future__ import annotations

import json
import re
import sys
import time
from urllib.parse import parse_qs, unquote, urlparse

try:
    from mitmproxy import http
except ImportError:  # `--selftest` runs on a plain Python without mitmproxy installed.
    class _Response:
        def __init__(self, status_code: int, content: bytes, headers: dict):
            self.status_code = status_code
            self.content = content
            self.headers = headers

        @classmethod
        def make(cls, status_code: int = 200, content=b"", headers=None):
            if isinstance(content, str):
                content = content.encode()
            return cls(status_code, content, dict(headers or {}))

    class http:  # noqa: N801 — mirrors the `mitmproxy.http` module name
        Response = _Response
        HTTPFlow = object

# --- knobs -------------------------------------------------------------------------------------------

BASE_URL = "{{BASE_URL_DEBUG}}"
HEALTH_PATH = "/{{HEALTH_PATH}}"

EXPIRES_IN = 3600
ENFORCE_EXPIRY = True

LIST_LATENCY = 0.0
DETAIL_LATENCY = 0.0
WRITE_LATENCY = 0.0

DETAIL_FAILURE = None
# Every refusal the real detail endpoint can produce. Typically they render the same message in the app and
# differ only in what the log shows — which is exactly what a test of "every refusal is handled" needs.
DETAIL_FAILURES = {
    "ITEM_NOT_FOUND": (404, "Item not found"),
    "ITEM_NOT_ALLOWED": (403, "The item is not available to this user"),
    "ITEM_STATUS_CHANGED": (409, "The item changed status"),
    "INTERNAL": (500, "Internal server error"),
}

ITEM_POOL_SIZE = 74
DEFAULT_PAGE_LIMIT = 30
MAX_PAGE_LIMIT = 100

USERS = {"user@example.test": "password"}
PROFILE = {"id": "user-1", "email": "user@example.test", "displayName": "Example User"}

# --- state (mutable on purpose: a write must be visible on the next read) -------------------------------

_API = urlparse(BASE_URL)
API_HOST = _API.hostname or ""
API_BASE_PATH = (_API.path or "/").rstrip("/")

_token_seq = 0
_access_tokens: dict[str, float] = {}   # access token -> expiry (epoch seconds)
_refresh_tokens: set[str] = set()       # refresh tokens not used yet (single-use)
_spent_refresh_tokens: set[str] = set()  # used once already: a second use is the bug we want loud
ITEMS: list[dict] = []
_idempotency: dict[str, tuple[int, dict]] = {}  # Idempotency-Key -> (status, data) of the first answer


def _reset() -> None:
    """(Re)seeds every piece of state. Runs at load — so a hot-reload starts from a clean session too."""
    global _token_seq
    _token_seq = 0
    _access_tokens.clear()
    _refresh_tokens.clear()
    _spent_refresh_tokens.clear()
    _idempotency.clear()
    ITEMS.clear()
    for i in range(1, ITEM_POOL_SIZE + 1):
        ITEMS.append({
            "id": f"item-{i}",
            # Shared prefixes on purpose, so a search visibly narrows the list.
            "title": f"{['Alpha', 'Beta', 'Gamma', 'Delta'][i % 4]} example {i}",
            "status": "open",
            "updatedAt": _iso(-60 * i),
            "completedAt": None,
            "note": None,
        })


# --- helpers -------------------------------------------------------------------------------------------

_QUIET = False  # the self-test silences the per-call log


def _log(what: str) -> None:
    if _QUIET:
        return
    print(f"[fake-be {time.strftime('%H:%M:%S')}] {what}", flush=True)


def _iso(offset_seconds: float = 0.0) -> str:
    """A server-clock stamp. Every timestamp the harness hands out comes from here, so durations never skew."""
    return time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(time.time() + offset_seconds)) + ".000Z"


def _respond(flow, status: int, payload) -> None:
    flow.response = http.Response.make(status, json.dumps(payload).encode(), {"Content-Type": "application/json"})


def _ok(flow, data, status: int = 200) -> None:
    """Success envelope of the example contract: {"data": …, "timestamp": …}."""
    _respond(flow, status, {"data": data, "timestamp": _iso()})


def _error(flow, status: int, code: str, message: str, details=None) -> None:
    """Error envelope of the example contract: {"error": {"error", "message", "details"}, "timestamp"}."""
    _respond(flow, status, {"error": {"error": code, "message": message, "details": details or []},
                            "timestamp": _iso()})


def _short(secret: str) -> str:
    return secret[:14] + "…" if len(secret) > 14 else secret


def _query(flow) -> dict:
    """Flat query parameters; a repeated key keeps its last value."""
    _, _, raw = flow.request.path.partition("?")
    return {k: v[-1] for k, v in parse_qs(raw).items()}


def _json_body(flow):
    """The JSON body, `{}` when empty, or None when it is not JSON. Never raises."""
    text = flow.request.get_text() or ""
    if not text.strip():
        return {}
    try:
        return json.loads(text)
    except (ValueError, TypeError):
        return None


def _tokens() -> dict:
    global _token_seq
    _token_seq += 1
    access, refresh = f"fake-access-{_token_seq}", f"fake-refresh-{_token_seq}"
    _access_tokens[access] = time.time() + EXPIRES_IN
    _refresh_tokens.add(refresh)
    return {"accessToken": access, "refreshToken": refresh, "expiresIn": EXPIRES_IN}


def _authorized(flow, label: str) -> bool:
    """True when the bearer is a live access token minted here; otherwise answers 401 and returns False."""
    header = flow.request.headers.get("Authorization") or ""
    token = header[len("Bearer "):] if header.startswith("Bearer ") else ""
    if not token:
        _log(f"{label} -> 401 TOKEN_MISSING")
        _error(flow, 401, "TOKEN_MISSING", "Authorization required")
        return False
    expiry = _access_tokens.get(token)
    if expiry is None:
        _log(f"{label} -> 401 TOKEN_INVALID ({_short(token)})")
        _error(flow, 401, "TOKEN_INVALID", "Unknown or revoked token")
        return False
    if ENFORCE_EXPIRY and time.time() >= expiry:
        _log(f"{label} -> 401 TOKEN_EXPIRED ({_short(token)})")
        _error(flow, 401, "TOKEN_EXPIRED", "Access token expired")
        return False
    return True


def _find_item(item_id: str):
    return next((i for i in ITEMS if i["id"] == item_id), None)


# --- handlers (example contract) -------------------------------------------------------------------------

def health(flow, label: str) -> None:
    _log(f"{label} -> 200")
    _respond(flow, 200, {"status": "ok"})


def login(flow, label: str) -> None:
    body = _json_body(flow)
    if not isinstance(body, dict) or not body.get("email") or not body.get("password"):
        _log(f"{label} -> 400 VALIDATION_ERROR (fields: {sorted(body) if isinstance(body, dict) else 'not JSON'})")
        _error(flow, 400, "VALIDATION_ERROR", "email and password are required")
        return
    if USERS.get(body["email"]) != body["password"]:
        _log(f"{label} -> 401 INVALID_CREDENTIALS ({body['email']})")
        _error(flow, 401, "INVALID_CREDENTIALS", "Wrong email or password")
        return
    tokens = _tokens()
    _log(f"{label} -> 200 ({body['email']}, access {_short(tokens['accessToken'])}, expiresIn={EXPIRES_IN})")
    _ok(flow, {**tokens, "user": PROFILE})


def refresh(flow, label: str) -> None:
    body = _json_body(flow)
    presented = body.get("refreshToken", "") if isinstance(body, dict) else ""
    if presented in _spent_refresh_tokens:
        # Two refreshes racing with the same token: the real API rejects the loser, and so must the fake,
        # or a client without single-flight refresh looks correct here and logs users out in production.
        _log(f"{label} -> 401 REFRESH_TOKEN_INVALID  *** REFRESH TOKEN REUSED *** ({_short(presented)})")
        _error(flow, 401, "REFRESH_TOKEN_INVALID", "Refresh token already used")
        return
    if presented not in _refresh_tokens:
        _log(f"{label} -> 401 REFRESH_TOKEN_INVALID ({_short(presented) or 'missing'})")
        _error(flow, 401, "REFRESH_TOKEN_INVALID", "Unknown refresh token")
        return
    _refresh_tokens.discard(presented)
    _spent_refresh_tokens.add(presented)
    tokens = _tokens()
    _log(f"{label} -> 200  *** A REFRESH HAPPENED *** (new access {_short(tokens['accessToken'])})")
    _ok(flow, tokens)


def logout(flow, label: str) -> None:
    if not _authorized(flow, label):
        return
    token = (flow.request.headers.get("Authorization") or "")[len("Bearer "):]
    _access_tokens.pop(token, None)
    body = _json_body(flow)
    if isinstance(body, dict) and body.get("refreshToken"):
        _refresh_tokens.discard(body["refreshToken"])
    _log(f"{label} -> 200 (session revoked)")
    _ok(flow, {"success": True})


def list_items(flow, label: str) -> None:
    if not _authorized(flow, label):
        return
    if LIST_LATENCY:
        time.sleep(LIST_LATENCY)
    q = _query(flow)
    try:
        page = int(q.get("page") or 1)
        limit = int(q.get("limit") or DEFAULT_PAGE_LIMIT)
    except ValueError:
        page = limit = 0
    if page < 1 or not 1 <= limit <= MAX_PAGE_LIMIT:
        _log(f"{label} -> 400 VALIDATION_ERROR (page={q.get('page')!r} limit={q.get('limit')!r})")
        _error(flow, 400, "VALIDATION_ERROR", f"page >= 1 and 1 <= limit <= {MAX_PAGE_LIMIT}")
        return
    search = (q.get("search") or "").strip().lower()
    status = q.get("status")
    matched = [i for i in ITEMS
               if (not search or search in i["title"].lower()) and (not status or i["status"] == status)]
    start = (page - 1) * limit
    window = matched[start:start + limit]
    has_next = start + limit < len(matched)
    _log(f"{label} -> 200 ({len(window)}/{len(matched)} items, page={page} limit={limit} "
         f"search={search!r} status={status!r} hasNext={has_next})")
    _ok(flow, {
        "items": window,
        "pagination": {
            "page": page,
            "limit": limit,
            "itemCount": len(matched),               # the total across pages, not the rows loaded so far
            "pageCount": max(1, -(-len(matched) // limit)),
            "hasPreviousPage": page > 1,
            "hasNextPage": has_next,
        },
    })


def item_detail(flow, label: str, item_id: str) -> None:
    if not _authorized(flow, label):
        return
    if DETAIL_LATENCY:
        time.sleep(DETAIL_LATENCY)
    if DETAIL_FAILURE:
        status, message = DETAIL_FAILURES[DETAIL_FAILURE]
        _log(f"{label} -> {status} {DETAIL_FAILURE} (forced by DETAIL_FAILURE)")
        _error(flow, status, DETAIL_FAILURE, message)
        return
    item = _find_item(item_id)
    if item is None:
        _log(f"{label} -> 404 ITEM_NOT_FOUND")
        _error(flow, 404, "ITEM_NOT_FOUND", "Item not found")
        return
    _log(f"{label} -> 200 ({item['status']})")
    _ok(flow, item)


ALLOWED_COMPLETE_FIELDS = {"note"}


def complete_item(flow, label: str, item_id: str) -> None:
    if not _authorized(flow, label):
        return
    if WRITE_LATENCY:
        time.sleep(WRITE_LATENCY)
    key = flow.request.headers.get("Idempotency-Key") or ""
    if not key:
        _log(f"{label} -> 400 IDEMPOTENCY_KEY_REQUIRED")
        _error(flow, 400, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required")
        return
    if key in _idempotency:
        status, data = _idempotency[key]
        _log(f"{label} -> {status} (replay of key {_short(key)})")
        _ok(flow, {**data, "isReplay": True}, status)
        return
    body = _json_body(flow)
    if not isinstance(body, dict):
        _log(f"{label} -> 400 VALIDATION_ERROR (body is not a JSON object)")
        _error(flow, 400, "VALIDATION_ERROR", "Body must be a JSON object")
        return
    # Unknown (or retired) fields are refused, not ignored: ignoring them would let a client that still
    # sends an old field look correct here and fail against the real API.
    unknown = sorted(set(body) - ALLOWED_COMPLETE_FIELDS)
    if unknown:
        _log(f"{label} -> 400 VALIDATION_ERROR (unknown fields {unknown})")
        _error(flow, 400, "VALIDATION_ERROR", f"Unknown fields: {', '.join(unknown)}")
        return
    item = _find_item(item_id)
    if item is None:
        _log(f"{label} -> 404 ITEM_NOT_FOUND")
        _error(flow, 404, "ITEM_NOT_FOUND", "Item not found")
        return
    if item["status"] != "open":
        _log(f"{label} -> 409 ITEM_STATUS_CHANGED (already {item['status']}, new key {_short(key)})")
        _error(flow, 409, "ITEM_STATUS_CHANGED", "The item is no longer open")
        return
    now = _iso()
    item.update(status="done", completedAt=now, updatedAt=now, note=body.get("note"))
    data = {"item": dict(item), "isReplay": False}
    _idempotency[key] = (200, data)
    _log(f"{label} -> 200 (open -> done, key {_short(key)})")
    _ok(flow, data)


# Method, path regex (relative to BASE_URL's path), handler. Named groups become handler kwargs.
ROUTES = [
    ("GET", re.escape(HEALTH_PATH), health),
    ("POST", r"/v1/auth/login", login),
    ("POST", r"/v1/auth/refresh", refresh),
    ("POST", r"/v1/auth/logout", logout),
    ("GET", r"/v1/items", list_items),
    ("GET", r"/v1/items/(?P<item_id>[^/]+)", item_detail),
    ("POST", r"/v1/items/(?P<item_id>[^/]+)/complete", complete_item),
]
_COMPILED = [(m, re.compile(f"^{p}$"), h) for m, p, h in ROUTES]


def request(flow) -> None:
    # A scenario loaded before this script already answered — it owns that endpoint.
    if flow.response is not None:
        return
    if flow.request.pretty_host != API_HOST:
        return  # not our API (e.g. system traffic on the emulator): pass through, unlogged
    full = flow.request.path.split("?", 1)[0]
    if API_BASE_PATH and not full.startswith(API_BASE_PATH + "/"):
        return
    path = unquote(full[len(API_BASE_PATH):]) or "/"
    method = flow.request.method
    label = f"{method:<4} {path}"
    for route_method, pattern, handler in _COMPILED:
        match = pattern.match(path)
        if match and route_method == method:
            handler(flow, label, **match.groupdict())
            return
    _log(f"UNHANDLED {label} — passing through to the real host")


_reset()


# --- self-test -----------------------------------------------------------------------------------------

class _Headers(dict):
    """Case-insensitive lookup, like mitmproxy's Headers."""

    def get(self, key, default=None):
        for k, v in self.items():
            if k.lower() == key.lower():
                return v
        return default


class _Request:
    def __init__(self, method: str, path: str, body=None, headers=None, host=None):
        self.method = method
        self.path = API_BASE_PATH + path
        self.pretty_host = API_HOST if host is None else host
        self.headers = _Headers(headers or {})
        self._text = body if isinstance(body, str) else (json.dumps(body) if body is not None else "")

    def get_text(self):
        return self._text


class _Flow:
    def __init__(self, *args, **kwargs):
        self.request = _Request(*args, **kwargs)
        self.response = None


def _call(method, path, body=None, token=None, headers=None, host=None):
    h = dict(headers or {})
    if token:
        h["Authorization"] = f"Bearer {token}"
    flow = _Flow(method, path, body, h, host)
    request(flow)
    if flow.response is None:
        return flow, None, None
    payload = json.loads(flow.response.content or b"null")
    return flow, flow.response.status_code, payload


def _selftest() -> int:
    global DETAIL_FAILURE, _QUIET
    _QUIET = True
    checks = 0
    failures = []

    def check(name, condition):
        nonlocal checks
        checks += 1
        if not condition:
            failures.append(name)

    _reset()
    _, s, _b = _call("GET", HEALTH_PATH)
    check("health answers 200 without auth", s == 200)
    _, s, b = _call("GET", "/v1/items")
    check("list without a token is 401 TOKEN_MISSING", s == 401 and b["error"]["error"] == "TOKEN_MISSING")
    _, s, b = _call("POST", "/v1/auth/login", {"email": "user@example.test"})
    check("login without password is 400", s == 400)
    _, s, b = _call("POST", "/v1/auth/login", {"email": "user@example.test", "password": "nope"})
    check("login with a wrong password is 401", s == 401 and b["error"]["error"] == "INVALID_CREDENTIALS")
    _, s, b = _call("POST", "/v1/auth/login", {"email": "user@example.test", "password": "password"})
    check("login succeeds", s == 200 and b["data"]["expiresIn"] == EXPIRES_IN and "user" in b["data"])
    access, refresh_token = b["data"]["accessToken"], b["data"]["refreshToken"]

    _, s, b = _call("GET", "/v1/items", token=access)
    p = b["data"]["pagination"]
    check("first page is full and has a next page",
          s == 200 and len(b["data"]["items"]) == DEFAULT_PAGE_LIMIT and p["hasNextPage"]
          and p["itemCount"] == ITEM_POOL_SIZE)
    _, s, b = _call("GET", "/v1/items?page=3&limit=30", token=access)
    check("last page is short and ends paging",
          s == 200 and len(b["data"]["items"]) == ITEM_POOL_SIZE - 60 and not b["data"]["pagination"]["hasNextPage"])
    _, s, b = _call("GET", "/v1/items?search=ALPHA", token=access)
    check("search is case-insensitive and narrows",
          s == 200 and 0 < b["data"]["pagination"]["itemCount"] < ITEM_POOL_SIZE)
    _, s, _b = _call("GET", "/v1/items?limit=1000", token=access)
    check("an out-of-range limit is 400", s == 400)
    _, s, b = _call("GET", "/v1/items/item-1", token=access)
    check("detail answers the item", s == 200 and b["data"]["id"] == "item-1")
    _, s, _b = _call("GET", "/v1/items/nope", token=access)
    check("unknown detail is 404", s == 404)
    DETAIL_FAILURE = "ITEM_NOT_ALLOWED"
    _, s, b = _call("GET", "/v1/items/item-1", token=access)
    check("DETAIL_FAILURE forces the refusal", s == 403 and b["error"]["error"] == "ITEM_NOT_ALLOWED")
    DETAIL_FAILURE = None

    _, s, _b = _call("POST", "/v1/items/item-2/complete", {}, token=access)
    check("a write without Idempotency-Key is 400", s == 400)
    _, s, _b = _call("POST", "/v1/items/item-2/complete", {"legacyField": 1}, token=access,
                     headers={"Idempotency-Key": "k-0"})
    check("an unknown field is refused", s == 400)
    _, s, b = _call("POST", "/v1/items/item-2/complete", {"note": "ok"}, token=access,
                    headers={"Idempotency-Key": "k-1"})
    check("complete flips open -> done", s == 200 and b["data"]["item"]["status"] == "done"
          and b["data"]["isReplay"] is False)
    _, s, b = _call("POST", "/v1/items/item-2/complete", {"note": "ok"}, token=access,
                    headers={"idempotency-key": "k-1"})
    check("same key replays the first answer", s == 200 and b["data"]["isReplay"] is True)
    _, s, b = _call("POST", "/v1/items/item-2/complete", {}, token=access, headers={"Idempotency-Key": "k-2"})
    check("a new key on a done item is 409", s == 409 and b["error"]["error"] == "ITEM_STATUS_CHANGED")
    _, s, b = _call("GET", "/v1/items/item-2", token=access)
    check("the write is visible on the next read", b["data"]["status"] == "done")

    _, s, b = _call("POST", "/v1/auth/refresh", {"refreshToken": refresh_token})
    check("refresh rotates the pair", s == 200 and b["data"]["refreshToken"] != refresh_token)
    new_access = b["data"]["accessToken"]
    _, s, b = _call("POST", "/v1/auth/refresh", {"refreshToken": refresh_token})
    check("a reused refresh token is refused", s == 401 and b["error"]["error"] == "REFRESH_TOKEN_INVALID")
    _access_tokens[new_access] = time.time() - 1
    _, s, b = _call("GET", "/v1/items", token=new_access)
    check("an expired access token is 401 TOKEN_EXPIRED", s == 401 and b["error"]["error"] == "TOKEN_EXPIRED")
    _access_tokens[new_access] = time.time() + EXPIRES_IN
    _, s, _b = _call("POST", "/v1/auth/logout", {}, token=new_access)
    check("logout succeeds", s == 200)
    _, s, b = _call("GET", "/v1/items", token=new_access)
    check("a logged-out token is 401 TOKEN_INVALID", s == 401 and b["error"]["error"] == "TOKEN_INVALID")

    flow = _Flow("GET", "/v1/items")
    flow.response = http.Response.make(418, b"scenario", {})
    request(flow)
    check("a scenario's answer is never overwritten", flow.response.status_code == 418)
    _, s, _b = _call("GET", "/v1/items", host="other.example.test")
    check("other hosts pass through untouched", s is None)
    _, s, _b = _call("DELETE", "/v1/items/item-1", token=access)
    check("unhandled routes pass through", s is None)

    _reset()
    if failures:
        print(f"selftest FAILED: {len(failures)}/{checks} checks", file=sys.stderr)
        for name in failures:
            print(f"  - {name}", file=sys.stderr)
        return 1
    print(f"selftest OK: {checks} checks (API host {API_HOST!r}, base path {API_BASE_PATH or '/'!r})")
    return 0


if __name__ == "__main__":
    if "--selftest" in sys.argv[1:]:
        sys.exit(_selftest())
    print(__doc__)
    print("This is a mitmproxy addon: load it with `mitmdump -s <this file>`. `--selftest` checks it offline.")
