# GET 403 — scope-lost

> Needs a feature that reads `GET /v1/items/{itemId}` (the example contract's detail endpoint). With only the
> components installed nothing calls it — keep this as the template for your own scope-level 403.

- **Endpoint:** `GET /v1/items/{itemId}`
- **Match:** exact method, path with one parameter segment (any base-path prefix), query ignored
- **Behavior:** short-circuit (`request` hook)
- **Mocked response:** `403`, body
  `{"error":{"error":"SCOPE_ACCESS_REVOKED","message":"…","details":[]},"timestamp":"…"}`
- **How to run it:** start mitmdump with this scenario first and the harness second; sign in; open any item.
- **Expected app behavior:** the access-loss interceptor reports `ScopeLost`; with no `ScopeRecovery` bound
  the host fallback ends the session as `ACCESS_CHANGED`: the app lands on sign-in with the "Your access has
  changed" notice. The detail screen shows nothing of its own (it swallows its `ScopeLost` rung).
- **What would be a bug:** a "no permission" banner on the detail screen *and* a session notice (double
  reaction); staying signed in; a token refresh after the 403 (a 403 is not an auth failure).
- **Contrast:** replace the code with `ITEM_NOT_ALLOWED` — the session must survive and only the "You do not
  have permission to do that" notice (or the feature's own message, if it filters its expected 403s) appears.
- **Run:**
  `mitmdump --set flow_detail=1 --set connection_strategy=lazy -s .mitm_scripts/v1_items_itemId/GET_403_scope-lost.py -s .mitm_scripts/_harness/fake_backend.py`
- **Related:** `references/access-loss.md` (ACCESS rules).
