#!/usr/bin/env bash
# Install smoke test: renders a scenario into a fresh copy of the stock "Empty Activity" fixture, runs every
# installed component's gates right after installing it, then builds and tests the result. The same script runs
# in CI (.github/workflows/spec-ci.yml) and on a maintainer's machine, so a red CI run is reproducible locally.
#
# Usage: tests/smoke/install-smoke.sh SCENARIO [--keep DIR]
#   SCENARIO  startup      Core components only, every question skipped (the "startup" dry run)
#             backend-app  Core + network, auth-session, connectivity, paging, ci-cd, mitm-testing, then images
#             everything   every component in manifest order (default answers), plus assembleRelease
#             long-names   every component with long install answers (app name, packages, light-dark theme) and a
#                          scaffolded feature with a long name: formatting (line length) must not depend on the
#                          default answers
#   --keep DIR  render into DIR (must not exist) and leave it there; default is a temp dir removed on success.
#
# Requires: python3, rsync, git, a JDK and an Android SDK (ANDROID_HOME) with the compileSdk platform.
# This covers the renderer's mechanical part only. Agent-driven steps of a component procedure
# (installer/components/<id>.md) are not exercised: if a component cannot reach a green build from
# `render.py install` alone, this script fails at that component's gate. Treat that as a finding: either
# document the agent-only step in the procedure, or move it into render.py.
set -euo pipefail

SPEC="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
scenario="${1:-}"; shift || true
keep=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --keep) keep="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 1 ;;
  esac
done

R() { python3 "$SPEC/installer/render.py" "$@"; }
step() { printf '\n=== [smoke:%s] %s\n' "$scenario" "$*"; }
fail() { printf '\n!!! [smoke:%s] FAILED: %s\n' "$scenario" "$*" >&2; exit 1; }

core=$(R manifest --tier core)
case "$scenario" in
  startup) components="$core"; added="" ;;
  # Order respects depends_on; `images` is added afterwards, as the backend-app dry run prescribes.
  backend-app) components="$core network auth-session connectivity paging ci-cd mitm-testing"; added="images" ;;
  # Manifest order respects depends_on (render.py install refuses otherwise).
  everything|long-names) components="$(R manifest)"; added="" ;;
  *) echo "usage: $0 startup|backend-app|everything|long-names [--keep DIR]" >&2; exit 1 ;;
esac

if [[ -n "$keep" ]]; then
  [[ -e "$keep" ]] && fail "--keep target $keep already exists"
  T="$keep"
else
  T="$(mktemp -d)/proj"
fi
mkdir -p "$(dirname "$T")"

step "fixture → $T"
cp -R "$SPEC/tests/fixtures/empty-activity" "$T"
(cd "$T" && git init -q)
mkdir -p "$T/.arch-spec"
rsync -a --exclude .git --exclude .idea --exclude tests/fixtures --exclude .no-commit-files \
  "$SPEC/" "$T/.arch-spec/spec/"
TR() { python3 "$T/.arch-spec/spec/installer/render.py" "$@"; }

step "init + skip every question"
TR init --target "$T" >/dev/null
# Every parameter the renderer knows is answered "-" (skipped): the safe defaults must compile.
params=$(python3 -c "import sys; sys.path.insert(0, '$SPEC/installer'); import render; print(' '.join(k + '=-' for k in render.DEFAULTS))")
# shellcheck disable=SC2086
TR set --target "$T" $params >/dev/null
if [[ "$scenario" == long-names ]]; then
  # Longer than every placeholder they replace, so template lines near the length limit show up here.
  TR set --target "$T" "APP_NAME=Field Operations Companion" APPLICATION_ID=org.acme.fieldoperations.companion \
    THEME_MODE=light-dark >/dev/null
fi

run_gates() {
  local c="$1" g
  while IFS= read -r g; do
    [[ -z "$g" ]] && continue
    step "gate $c: $g"
    (cd "$T" && bash -c "$g") || fail "gate of '$c': $g"
  done < <(R manifest --gates "$c")
}

for c in $components $added; do
  step "install $c"
  TR install --target "$T" "$c" --fresh >/dev/null || fail "render.py install $c"
  run_gates "$c"
done

if [[ "$scenario" == long-names ]]; then
  step "scaffold a long-named feature"
  TR scaffold-feature --target "$T" inventory-reconciliation >/dev/null || fail "scaffold-feature"
fi

step "build + unit tests"
(cd "$T" && ./gradlew assembleDebug testDebugUnitTest --console=plain) || fail "assembleDebug testDebugUnitTest"
if [[ "$scenario" == everything || "$scenario" == long-names ]]; then
  step "release build"
  (cd "$T" && ./gradlew assembleRelease --console=plain) || fail "assembleRelease"
fi

# The installed project's own quality gates, over everything installed (formatting, module rules, lint), with
# configuration-cache problems as errors (BUILD-14).
step "spotlessCheck checkModuleGraph lint"
(cd "$T" && ./gradlew spotlessCheck checkModuleGraph :app:lintDebug :build-logic:convention:test \
  --configuration-cache-problems=fail --console=plain) || fail "spotlessCheck checkModuleGraph :app:lintDebug"

step "post-install gates"
bash "$SPEC/installer/gates/no-placeholders-left.sh" "$T" || fail "no-placeholders-left"
bash "$SPEC/installer/gates/skills-lint.sh" "$T" || fail "skills-lint"
if [[ -f "$T/.arch-spec/spec/installer/gates/test-summary.sh" ]]; then
  bash "$T/.arch-spec/spec/installer/gates/test-summary.sh" "$T" || true
fi

step "OK"
if [[ -z "$keep" ]]; then rm -rf "$(dirname "$T")"; else echo "kept: $T"; fi
