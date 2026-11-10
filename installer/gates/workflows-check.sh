#!/usr/bin/env bash
# Checks the GitHub Actions workflows of a project: no unrendered {{PLACEHOLDER}}, valid YAML, the expected top-level
# keys, every `run:` script parses with bash -n. Uses actionlint when installed (strongest), else ruby or python
# yaml for parsing; reports which level of checking actually ran so a weak gate is never mistaken for a strong one.
# Usage: workflows-check.sh [PROJECT_DIR]
set -euo pipefail
dir="${1:-.}"
wf="$dir/.github/workflows"
[[ -d "$wf" ]] || { echo "workflows-check: no $wf — nothing to check"; exit 0; }
status=0
if grep -RnE '\{\{[A-Z][A-Z0-9_]*\}\}' "$wf" >/dev/null 2>&1; then
  echo "workflows-check: FAIL — unrendered placeholders:"; grep -RnE '\{\{[A-Z][A-Z0-9_]*\}\}' "$wf"; status=1
fi
for f in "$wf"/*.yml "$wf"/*.yaml; do
  [[ -f "$f" ]] || continue
  for key in '^on:' '^jobs:' '^name:'; do
    grep -qE "$key" "$f" || { echo "workflows-check: FAIL — $f has no top-level ${key#^}"; status=1; }
  done
done
if command -v actionlint >/dev/null 2>&1; then
  (cd "$dir" && actionlint) || status=1; level="actionlint"
elif command -v ruby >/dev/null 2>&1; then
  for f in "$wf"/*.y*ml; do
    ruby -ryaml -e 'y = YAML.load_file(ARGV[0]); (y["jobs"] || {}).each_value { |j| (j["steps"] || []).each_with_index { |s, i| next unless s["run"]; File.write("/tmp/.wfc_#{$$}_#{i}.sh", s["run"]) } }' "$f" \
      || { echo "workflows-check: FAIL — YAML parse error in $f"; status=1; }
    for s in /tmp/.wfc_*.sh; do [[ -f "$s" ]] || continue; bash -n "$s" 2>/dev/null || { echo "workflows-check: FAIL — a run: script in $f does not parse"; status=1; }; rm -f "$s"; done
  done
  level="ruby YAML parse + bash -n of run: scripts (install actionlint for full checks)"
elif python3 -c 'import yaml' >/dev/null 2>&1; then
  for f in "$wf"/*.y*ml; do python3 -c 'import sys,yaml; yaml.safe_load(open(sys.argv[1]))' "$f" || { echo "workflows-check: FAIL — YAML parse error in $f"; status=1; }; done
  level="python YAML parse (install actionlint for full checks)"
else
  level="structure only — no YAML parser or actionlint available (install actionlint for real checks)"
fi
[[ $status -eq 0 ]] && echo "workflows-check: OK — level: $level"
exit $status
