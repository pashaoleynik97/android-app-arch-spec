#!/usr/bin/env bash
# Prints unit-test counts per module from the JUnit XML results of the last `testDebugUnitTest` run, plus a total.
# Usage: test-summary.sh [PROJECT_DIR]   — exits 1 if any test failed or errored, 2 if no results were found.
set -euo pipefail
dir="${1:-.}"
python3 -I - "$dir" <<'PY'
import sys, pathlib, xml.etree.ElementTree as ET
root = pathlib.Path(sys.argv[1])
rows = {}
for f in root.glob("**/build/test-results/testDebugUnitTest/TEST-*.xml"):
    if ".arch-spec" in f.parts:
        continue
    module = f.relative_to(root).parts[0]
    s = ET.parse(f).getroot()
    r = rows.setdefault(module, [0, 0, 0, 0])
    r[0] += int(s.get("tests", 0)); r[1] += int(s.get("failures", 0))
    r[2] += int(s.get("errors", 0)); r[3] += int(s.get("skipped", 0))
if not rows:
    print("test-summary: no results under */build/test-results/testDebugUnitTest — run ./gradlew testDebugUnitTest first")
    sys.exit(2)
print(f"{'module':<28}{'tests':>7}{'failed':>8}{'errors':>8}{'skipped':>9}")
tot = [0, 0, 0, 0]
for m in sorted(rows):
    r = rows[m]; tot = [a + b for a, b in zip(tot, r)]
    print(f"{m:<28}{r[0]:>7}{r[1]:>8}{r[2]:>8}{r[3]:>9}")
print(f"{'TOTAL':<28}{tot[0]:>7}{tot[1]:>8}{tot[2]:>8}{tot[3]:>9}")
sys.exit(1 if tot[1] or tot[2] else 0)
PY
