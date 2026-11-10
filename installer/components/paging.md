# Component: paging (Recommended)

Rules: `specs/recommended/paging.md` (PAGE-01 … PAGE-13); decision `specs/decisions/ADR-0001-own-pager-over-paging-library.md`;
recipe `specs/patterns/paginated-picker.md`. Shorthands as in `INSTALL.md`: `SPEC=.arch-spec/spec`,
`R` = `python3 $SPEC/installer/render.py --target .`

## What it adds

New files only — inside the Core modules, no new module, no Core file overwritten:

| File | Purpose |
|---|---|
| `lib-common/…/libcommon/paging/Paging.kt` | `PagingConfig`, `Page`, `PageLoadOutcome`, `PageSource`, `PagingState` |
| `lib-common/…/libcommon/paging/Pager.kt` | the engine: single-flight, generation guard, equal-key no-op, refresh clears, append retry, `itemKey` de-dup |
| `lib-common/src/test/…/paging/{PagerTest,PagingConfigTest,FakePageSource}.kt` | tests pinning every rule; the hand-answered fake source to copy |
| `lib-android/…/libandroid/ui/compose/paging/PagingScrollTrigger.kt` | last-visible-index trigger |
| `lib-android/…/libandroid/ui/compose/paging/{{THEME_PREFIX}}PagingFooter.kt` | `pagingFooter(…)`, `{{THEME_PREFIX}}PagingLoadingItem`, `{{THEME_PREFIX}}PagingRetryItem` |
| `.claude/skills/application-architecture/references/paging.md` | engine rules, VM wiring, DI, tests |
| `.claude/skills/compose-ui/references/paging-ui.md` | screen shape, footer rules |

Marker edits: one index line in each skill's `references` block and one line in `CLAUDE.md` `components`.

## Prerequisites

- `architecture` installed (provides `:lib-common`, `:lib-android`, the theme and the base kit the footer uses).
- No library: the engine is plain coroutines.

## Questions (all skippable)

None. Paging tuning is per screen (`PagingConfig` under a qualifier, PAGE-07), decided when a paginated screen is
built — not at install time.

## Install

```
R plan paging
R install paging
```

No manual steps. If the project already has files at the same paths (exit code 2 → `.arch-spec-new`), compare and
merge by hand; do not overwrite a project's own pager silently.

## Gate

```
./gradlew assembleDebug
./gradlew :lib-common:testDebugUnitTest
```

## Completing deferred items

Nothing is deferred.

## Troubleshooting

| Symptom | Fix |
|---|---|
| The first page is skipped or repeated | `PagingConfig.firstPage` does not match the backend (0- vs 1-based) |
| Appends never start | `PagingScrollTrigger` gets a different `LazyListState` than the list, or the source maps `hasNext` wrongly (an `endReached` list never appends) |
| Appends fire repeatedly after a failure | the VM calls `refresh()` instead of `retry()` from the footer, or builds a new `Pager` on recomposition |
| "Key … was already used" crash in `LazyColumn` | pass `itemKey` to the `Pager` (PAGE-06) |
| An empty "no results" flash on entry | `submitKey(initialKey)` runs after `uiState`'s `stateIn` (PAGE-01) |

## Commit message

`chore(arch-spec): paging [spec vX.Y.Z]`
