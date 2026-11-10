#!/usr/bin/env bash
# Maintainer gate: fails if wording from a private source project leaks into the spec or into a rendered project.
#
# The deny-list is deliberately NOT part of this repository: put one lowercase word per line in a local file and
# point the gate at it (the default path is git-ignored). Without a list, only the English-only check runs.
#   default list: installer/gates/.domain-leak-denylist   (or set DOMAIN_LEAK_DENYLIST=/path/to/list)
#   a line `ticket:<prefix>` flags `<prefix>-<digits>` issue keys (e.g. `ticket:abc` → ABC-123).
# Non-English (Cyrillic) prose is always flagged: the spec is English-only.
#
# Usage: domain-leak.sh DIR
#   DIR with .arch-spec/state.json → checks only the files the renderer wrote (the user's own code is not judged).
#   any other DIR (e.g. the spec repo) → every text file except .git/, build/, .gradle/, .idea/, .arch-spec/.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
list="${DOMAIN_LEAK_DENYLIST:-$here/.domain-leak-denylist}"
python3 -I - "${1:-.}" "$list" <<'PY'
import json, pathlib, re, sys
root, list_path = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
words, tickets = set(), set()
if list_path.is_file():
    for raw in list_path.read_text(encoding="utf-8").splitlines():
        w = raw.strip().lower()
        if not w or w.startswith("#"):
            continue
        (tickets.add(w[7:]) if w.startswith("ticket:") else words.add(w))
CYRILLIC = re.compile(r"[Ѐ-ӿ]")
TOKEN = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")
SKIP_DIRS = {".git", "build", ".gradle", ".idea", ".arch-spec", "__pycache__"}

def hits(text):
    for n, line in enumerate(text.splitlines(), 1):
        if CYRILLIC.search(line):
            yield n, "non-English (Cyrillic) text"
        for tok in TOKEN.findall(line.lower()):
            parts = tok.split("-")
            if {tok, tok.replace("-", ""), *parts} & words:
                yield n, "denied word"; break
            if any(p in tickets and i + 1 < len(parts) and parts[i + 1].isdigit() for i, p in enumerate(parts)):
                yield n, "denied ticket key"; break

state = root / ".arch-spec" / "state.json"
if state.exists():
    files = [root / f for f in json.loads(state.read_text())["files"]]
else:
    files = [p for p in root.rglob("*") if p.is_file() and not (SKIP_DIRS & set(p.relative_to(root).parts))]
bad = []
for f in files:
    if not f.is_file() or f.name in ("domain-leak.sh", ".domain-leak-denylist"):
        continue
    try:
        text = f.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    bad += [f"{f.relative_to(root)}:{n}: {why}" for n, why in hits(text)]
mode = f"deny-list: {len(words)} words, {len(tickets)} ticket prefixes" if words or tickets else "no deny-list (English-only check)"
if bad:
    print(f"domain-leak: FAIL ({mode})"); print("\n".join(bad)); sys.exit(1)
print(f"domain-leak: OK ({mode})")
PY
