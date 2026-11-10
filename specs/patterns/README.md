# Patterns — domain-neutral recipes

Tier: **Recommended** (SHOULD). Each recipe combines Core rules (`specs/50-presentation.md`,
`specs/60-data-and-errors.md`, `specs/70-testing.md`) into a shape that recurs across features. Use a recipe when
its *Problem* matches your screen; adapt names freely. Each file has the same sections: **Problem · Shape
(modules + types) · Rules · Pitfalls · Test checklist**.

| Recipe | Use when | Key ideas |
|---|---|---|
| [selection-cascade.md](selection-cascade.md) | the user must pick A, then B (then …) before the app is usable | auto-skip single-option steps (one-shot), re-root with `popUpTo`, blocking dead-end screens by reason, resume reload never moves the user |
| [app-wide-cached-list.md](app-wide-cached-list.md) | one list is needed on many screens and must open instantly | singleton cache with own scope, polling gated by session + foreground, TTL, single-flight refresh, VM as projection, last-tap-wins re-check |
| [details-item-finish.md](details-item-finish.md) | a details screen with item cards and a final "finish" action | one read backs the flow, snapshot handoff via a store, item card opens without a request, write-through, idempotent finish with bounded retry |
| [live-detail.md](live-detail.md) | a detail screen others change concurrently, with running timers | resume-gated poll + local tick from a monotonic anchor |
| [paginated-picker.md](paginated-picker.md) | pick one or more entries from a large, searchable server list | `Pager` + debounced key + `PagingScrollTrigger` + per-row idempotent actions |
| [capture-for-result.md](capture-for-result.md) | a screen that uses hardware (camera, scanner) and returns one result | permission state machine, composable owns the hardware API, single in-flight guard, result via the previous back-stack entry |
| [configurable-multi-pane.md](configurable-multi-pane.md) | one screen reused in several flows with different settings and inner panes | route token → config, panes as `UxState` variants, VM-owned back |
| [exit-flow.md](exit-flow.md) | a screen must leave on its own (item gone, flow completed) | `SharedFlow(replay = 1)` exit, the feature impl pops the feature root and flags the landing entry |

Vocabulary used throughout: **feature** = a trio `:x-ui-api` / `:x-ui` / `:x`; **host** = `:app`; **seam** = a
nullable constructor parameter of `XFeatureImpl` or an unbound per-feature `fun interface` that the host fills in;
**outcome** = a feature-level sealed result returned by an interactor (DATA-03); **rung** = a classified service
result (DATA-11).
