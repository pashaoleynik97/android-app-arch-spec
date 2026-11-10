#!/usr/bin/env bash
# Fails if a {{PLACEHOLDER}} survived rendering, or a conflict file (.arch-spec-new) is still unresolved.
# Usage: no-placeholders-left.sh TARGET_DIR
set -euo pipefail
dir="${1:-.}"
status=0
hits=$(grep -RnIE '\{\{[A-Z][A-Z0-9_]*\}\}' "$dir" \
  --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.arch-spec 2>/dev/null || true)
if [[ -n "$hits" ]]; then
  echo "no-placeholders-left: FAIL — unrendered placeholders:"; echo "$hits"; status=1
fi
pending=$(find "$dir" -name '*.arch-spec-new' -not -path '*/.git/*' 2>/dev/null || true)
if [[ -n "$pending" ]]; then
  echo "no-placeholders-left: FAIL — unresolved conflicts (merge, then delete):"; echo "$pending"; status=1
fi
[[ $status -eq 0 ]] && echo "no-placeholders-left: OK"
exit $status
