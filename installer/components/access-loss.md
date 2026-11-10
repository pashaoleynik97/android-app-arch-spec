# Component: access-loss (Recommended)

Rules: `specs/recommended/access-loss.md` (ACCESS-01 … ACCESS-08); ADR-0005.

## What it adds

| File | Purpose |
|---|---|
| `network/src/main/java/…/network/access/*` | `AccessLoss`, `AccessLossBus` + `DefaultAccessLossBus`, `AccessLossFilter`, `AccessLossInterceptor`, `AccessLossConfig`, `RecoveryBudget`, `ScopeRecovery`, `di/AccessLossModule` |
| `network/src/test/java/…/network/access/*` | bus, interceptor, budget tests |
| `auth/src/main/java/…/auth/access/AccessLossFallback.kt` (+ test) | the host's terminal handler: notice for `Forbidden`, recovery or end of session for `ScopeLost` |
| `app/src/main/java/<app package>/access/AccessLossHostExtension.kt`, `di/AccessLossHostModule.kt`, `res/values/access_strings.xml` | the notice layer of the host |
| `.mitm_scripts/v1_items_itemId/GET_403_scope-lost.{py,md}` | device scenario template |
| `.claude/skills/application-architecture/references/access-loss.md` | skill reference |

It adds **files** to `:network` and `:auth` and contributes its interceptor through `:network`'s
`ApiInterceptor` set — no existing file is edited and no module is added.

## Prerequisites

`auth-session` installed (and therefore `network`).

## Questions

None. Tell the user the scope-level 403 code is an **example** (`SCOPE_ACCESS_REVOKED`, in
`AccessLossModule.provideAccessLossConfig()`).

## Install

```
R plan access-loss
R install access-loss
```

## Gate

```
./gradlew assembleDebug
./gradlew :network:testDebugUnitTest :auth:testDebugUnitTest
```

## Completing deferred items

None.

## Troubleshooting

| Symptom | Fix |
|---|---|
| A feature's 403 shows both its own message and "You do not have permission" | contribute an `AccessLossFilter` for that endpoint + code (reference, section 3) |
| Signed out on every 403 | the code is listed in `scopeLevelCodes` — adapt `AccessLossConfig` |
| Two `ScopeRecovery` bindings | only the first is used; bind one |

## Commit message

`chore(arch-spec): access-loss [spec vX.Y.Z]`
