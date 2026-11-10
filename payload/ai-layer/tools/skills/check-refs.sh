#!/usr/bin/env bash
# Checks that every inline `token` in skills, .claude/rules/*.md and CLAUDE.md still resolves:
# paths exist, modules are included in settings.gradle.kts, Gradle tasks name an existing module,
# PascalCase types are declared in some src/**/*.kt (warning) — or listed in tools/skills/check-refs.ignore.
# Fenced code blocks and lines containing <!-- check-refs: ignore --> are skipped.
# Usage: bash tools/skills/check-refs.sh [--strict]      (run from the repository root)
# Exit: 0 ok · 1 errors (or warnings with --strict) · 2 usage. Details: tools/skills/skills_tool.py.
set -eu
here="$(cd "$(dirname "$0")" && pwd)"
root="$(cd "$here/../.." && pwd)"
if ! command -v python3 >/dev/null 2>&1; then
  echo "check-refs: python3 is required (standard library only)" >&2
  exit 2
fi
exec python3 -I "$here/skills_tool.py" check-refs --root "$root" "$@"
