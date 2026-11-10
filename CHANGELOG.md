# Changelog

Versions follow semver. **MAJOR** changes require manual migration notes, **MINOR** adds components or rules, and
**PATCH** fixes the spec or payload without changing any rule. Each entry lists what changes in an installed project
and how the **upgrade** procedure (INSTALL.md §7) handles it.

## 0.1.0 — initial

- Core components: `foundation`, `ai-layer`, `architecture` (incl. design system and test kit), `docs`.
- Recommended components: `ci-cd`, `mitm-testing`, `network`, `auth-session`, `access-loss`, `connectivity`,
  `paging`, `images`, `media-store`, `hardware-input`, `engineering-menu`.
- `installer/render.py`: deterministic rendering (contents and paths), checksums with `.arch-spec-new` conflict
  files, deferred items with how-to lines, marker-block inserts, `snapshot` / `rebase` for three-way upgrades,
  `relocate` for renamed modules, recorded install order.
- Gates: `no-placeholders-left`, `skills-lint`, `test-summary`, `workflows-check`, and the maintainer-only
  `domain-leak`.
- Project skill tooling: `tools/skills/{lint,check-refs}.sh`, and `skills-maintenance` procedures (amend, capture,
  audit, hygiene, new-skill, upgrade, rename-module).
- Verified by three agent-driven dry runs (startup, backend app + add component, skill evolution + upgrade) and a
  full install of every component.
