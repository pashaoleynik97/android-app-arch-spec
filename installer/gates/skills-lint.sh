#!/usr/bin/env bash
# Lints Claude Code skills: frontmatter present + valid-looking, name == folder, description length,
# SKILL.md size budget, references one level deep, no duplicate rule IDs inside a skill.
# Usage: skills-lint.sh ROOT   (finds every */.claude/skills/*/SKILL.md and skills/*/SKILL.md under ROOT)
# Budgets: SKILL.md warn > 300 lines, fail > 500; references/*.md warn > 400 lines; description ≤ 1024 chars.
set -euo pipefail
root="${1:-.}"
fail=0; warn=0
skills=$(find "$root" -path '*/node_modules' -prune -o -name SKILL.md -path '*skills/*' -print 2>/dev/null | grep -v '/build/' || true)
[[ -z "$skills" ]] && { echo "skills-lint: no skills found under $root"; exit 0; }
while IFS= read -r skill; do
  dir=$(dirname "$skill"); folder=$(basename "$dir")
  if [[ "$(head -1 "$skill")" != "---" ]]; then echo "FAIL $skill: no YAML frontmatter"; fail=1; continue; fi
  fm=$(awk 'NR==1{next} /^---$/{exit} {print}' "$skill")
  name=$(printf '%s\n' "$fm" | sed -n 's/^name:[[:space:]]*//p' | head -1 | tr -d '"'"'"'')
  if [[ -z "$name" ]]; then echo "FAIL $skill: frontmatter has no name"; fail=1
  elif [[ "$name" != "$folder" ]]; then echo "FAIL $skill: name '$name' != folder '$folder'"; fail=1; fi
  if ! printf '%s\n' "$fm" | grep -q '^description:'; then echo "FAIL $skill: no description"; fail=1; fi
  desc=$(printf '%s\n' "$fm" | awk '/^description:/{f=1; sub(/^description:[[:space:]]*>?-?[[:space:]]*/,""); print; next} f && /^[a-z_-]+:/{f=0} f{print}' | tr -s ' \n' ' ')
  if (( ${#desc} > 1024 )); then echo "FAIL $skill: description ${#desc} chars > 1024"; fail=1; fi
  if printf '%s\n' "$fm" | grep -qE '^\s*\t'; then echo "FAIL $skill: tab in frontmatter"; fail=1; fi
  lines=$(wc -l < "$skill" | tr -d ' ')
  if (( lines > 500 )); then echo "FAIL $skill: $lines lines > 500 — split into references/"; fail=1
  elif (( lines > 300 )); then echo "WARN $skill: $lines lines > 300"; warn=1; fi
  if [[ -d "$dir/references" ]]; then
    nested=$(find "$dir/references" -mindepth 2 -type f | head -1)
    [[ -n "$nested" ]] && { echo "FAIL $dir/references: nested folders (keep one level deep): $nested"; fail=1; }
    while IFS= read -r ref; do
      rl=$(wc -l < "$ref" | tr -d ' ')
      (( rl > 400 )) && { echo "WARN $ref: $rl lines > 400"; warn=1; }
    done < <(find "$dir/references" -name '*.md' -type f)
  fi
  dups=$(grep -rhoE '\*\*[A-Z]+-[0-9]{2,3}\*\*' "$dir" 2>/dev/null | sort | uniq -d || true)
  [[ -n "$dups" ]] && { echo "FAIL $dir: rule IDs defined twice: $(echo $dups)"; fail=1; }
done <<< "$skills"
if (( fail )); then echo "skills-lint: FAIL"; exit 1; fi
(( warn )) && echo "skills-lint: OK (with warnings)" || echo "skills-lint: OK"
