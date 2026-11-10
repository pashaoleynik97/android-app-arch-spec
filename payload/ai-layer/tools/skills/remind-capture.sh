#!/usr/bin/env bash
# Optional Claude Code `Stop` hook (off by default; see the skills-maintenance skill, "Reminder hook").
# Reads the hook JSON on stdin. If the session changed convention-bearing files (Gradle build files, the
# version catalog, build-logic, a new module, the UI kit) but nothing under .claude/skills/ or .docs/, it asks
# the agent once to run the capture checklist. Otherwise it prints nothing and lets the session stop.
# Never fails the session: any unexpected condition exits 0 silently.
set -u

input="$(cat 2>/dev/null || true)"
# The guard that makes the reminder fire once: a Stop hook re-entered after blocking has this flag set.
if printf '%s' "$input" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

root="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$root" || exit 0

changed="$( { git diff --name-only HEAD 2>/dev/null || git diff --name-only --cached 2>/dev/null; \
              git ls-files --others --exclude-standard 2>/dev/null; } | sort -u )"
[ -z "$changed" ] && exit 0

hits=""
skills_touched=0
while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    .claude/skills/*|.docs/*) skills_touched=1; continue ;;
  esac
  case "$f" in
    *.gradle.kts|gradle/libs.versions.toml|build-logic/*|*/libandroid/ui/kit/*|*/libandroid/ui/*uikit/*)
      hits="$hits $f" ;;
    */src/main/AndroidManifest.xml)
      # A manifest that git does not know yet marks a new module directory.
      if ! git ls-files --error-unmatch "$f" >/dev/null 2>&1; then hits="$hits $f"; fi ;;
  esac
done <<EOF
$changed
EOF

[ "$skills_touched" -eq 1 ] && exit 0
[ -z "$hits" ] && exit 0

list="$(printf '%s' "$hits" | sed -e 's/^ //' -e 's/ /, /g' -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
printf '{"decision":"block","reason":"Convention-bearing files changed (%s). Run the skills-maintenance capture checklist, or state why no skill change is needed."}\n' "$list"
exit 0
