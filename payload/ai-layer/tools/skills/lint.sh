#!/usr/bin/env bash
# Lints the AI layer: every .claude/skills/*/SKILL.md (+ references), .claude/rules/*.md and CLAUDE.md.
# Checks frontmatter, size budgets, reference links, rule IDs, origin markers, override blocks.
# Usage: bash tools/skills/lint.sh [--strict]      (run from the repository root)
# Exit: 0 ok · 1 errors (or warnings with --strict) · 2 usage. Details: tools/skills/skills_tool.py.
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
if ! command -v python3 >/dev/null 2>&1; then
  echo "lint: python3 is required (standard library only)" >&2
  exit 2
fi
exec python3 -I "$here/skills_tool.py" lint --root "$root" "$@"
