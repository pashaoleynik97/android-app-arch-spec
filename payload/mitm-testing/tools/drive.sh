#!/usr/bin/env bash
# drive.sh — emulator helper for mitmproxy testing and on-demand feature verification.
# Not app code. Every command is one step of the workflow in the `feature-verification` skill.
# Works on macOS and Linux (bash 3.2+, python3 for UI-dump parsing). Run `bash tools/drive.sh help`.
set -euo pipefail

APP_ID="${APP_ID:-{{APPLICATION_ID}}}"
OUT="${OUT:-evidence}"                          # evidence folder: dumps, screenshots, logs
PROXY="${PROXY:-10.0.2.2:8080}"                 # the host machine as seen from the emulator
MITM_PORT="${MITM_PORT:-8080}"
HARNESS="${HARNESS:-.mitm_scripts/_harness/fake_backend.py}"
ADB="${ADB:-adb}"                               # honours ANDROID_SERIAL for multi-device setups

usage() {
  cat <<EOF
Usage: bash tools/drive.sh <command> [args]          (env: APP_ID OUT PROXY MITM_PORT HARNESS ANDROID_SERIAL)

Device
  boot [AVD]              wait for a booted device; start AVD first if none is connected
  install                 ./gradlew :app:installDebug (debug builds only trust the proxy CA)
  launch                  start $APP_ID
  clear-data              wipe $APP_ID's data (signed out, caches gone)
  proxy-on [HOST:PORT]    route the emulator through mitmproxy (default $PROXY)
  proxy-off               remove the proxy setting
  ca                      push the mitmproxy CA and print the one-time install steps

mitmproxy
  mitm SCENARIO.py ...    (re)start mitmdump in the background: scenarios FIRST, then the harness
                          (add --no-harness to omit it); log appended to \$OUT/mitm.log
  mitm-stop               stop the mitmdump started by this script
  requests [PATTERN]      print the timestamped request lines from \$OUT/mitm.log (optionally grep)

UI
  dump NAME               uiautomator dump -> \$OUT/NAME.xml and a flat \$OUT/NAME.txt ("[l,t][r,b] 'text'")
  find TEXT               print bounds + centre of nodes whose text/content-desc contains TEXT
  tap-text TEXT           dump, find TEXT (first match), tap its centre
  tap X Y | swipe X1 Y1 X2 Y2 [MS] | type TEXT | key KEYCODE   raw input
  shot NAME               screenshot -> \$OUT/NAME.png
  burst NAME [N] [SEC]    N screenshots SEC apart (default 6, 1) -> \$OUT/NAME-01.png ...

Logs
  logcat [PATTERN]        app-process logcat (Reporter lines), optionally filtered by PATTERN; Ctrl-C stops
  logcat-dump NAME [PAT]  the current app-process logcat buffer -> \$OUT/NAME.log

Undo everything: bash tools/drive.sh proxy-off && bash tools/drive.sh mitm-stop
EOF
}

die() { echo "drive.sh: $*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "'$1' not found on PATH"; }
outdir() { mkdir -p "$OUT"; }

booted() { [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; }

cmd_boot() {
  need "$ADB"
  if ! "$ADB" get-state >/dev/null 2>&1; then
    [ $# -ge 1 ] || die "no device connected; pass an AVD name (emulator -list-avds)"
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    [ -x "$sdk/emulator/emulator" ] || sdk="$HOME/Android/Sdk"
    [ -x "$sdk/emulator/emulator" ] || die "emulator binary not found; set ANDROID_HOME"
    nohup "$sdk/emulator/emulator" -avd "$1" -no-snapshot-load >/dev/null 2>&1 &
  fi
  "$ADB" wait-for-device
  local i=0
  until booted; do
    i=$((i + 1)); [ $i -le 120 ] || die "device did not finish booting in 4 minutes"
    sleep 2
  done
  echo "device booted: $("$ADB" get-serialno)"
}

cmd_ca() {
  local cert="$HOME/.mitmproxy/mitmproxy-ca-cert.cer"
  [ -f "$cert" ] || die "$cert missing — run mitmdump once to generate it"
  "$ADB" push "$cert" /sdcard/Download/mitmproxy-ca.crt >/dev/null
  cat <<'EOF'
CA pushed to /sdcard/Download/mitmproxy-ca.crt. One-time install (user CA store):
  1. A screen lock is required first:   adb shell locksettings set-pin 1234
  2. Settings > Security > Encryption & credentials > Install a certificate > CA certificate
     > Downloads > mitmproxy-ca.crt   (drive it with: drive.sh tap-text "<label>")
  3. Check: Settings > ... > User credentials lists "mitmproxy".
A direct file:// VIEW intent to the certificate installer fails under scoped storage — use the picker.
On a non-Play (AOSP) image `adb root` works and the CA can go into the system store instead.
Undo the PIN later: adb shell locksettings clear --old 1234
EOF
}

cmd_mitm() {
  need mitmdump; outdir
  local harness=1 args=() s
  cmd_mitm_stop quiet
  for s in "$@"; do
    if [ "$s" = "--no-harness" ]; then harness=0; continue; fi
    [ -f "$s" ] || die "scenario not found: $s"
    args+=(-s "$s")
  done
  [ $harness = 0 ] || args+=(-s "$HARNESS")
  [ ${#args[@]} -gt 0 ] || die "nothing to load"
  echo "=== $(date '+%H:%M:%S') mitm start: $*" >> "$OUT/mitm.log"
  # flow_detail=1: with 0 a short-circuiting scenario answers silently. connection_strategy=lazy: flows
  # answered locally never dial the real host (which may not exist yet).
  nohup mitmdump -p "$MITM_PORT" --set flow_detail=1 --set connection_strategy=lazy "${args[@]}" \
    >> "$OUT/mitm.log" 2>&1 &
  echo $! > "$OUT/.mitm.pid"
  sleep 2
  kill -0 "$(cat "$OUT/.mitm.pid")" 2>/dev/null || { tail -20 "$OUT/mitm.log"; die "mitmdump exited"; }
  echo "mitmdump running (pid $(cat "$OUT/.mitm.pid"), port $MITM_PORT), log: $OUT/mitm.log"
}

cmd_mitm_stop() {
  local pidfile="$OUT/.mitm.pid"
  if [ -f "$pidfile" ]; then
    kill "$(cat "$pidfile")" 2>/dev/null || true
    rm -f "$pidfile"
    sleep 1
    [ "${1:-}" = quiet ] || echo "mitmdump stopped"
  elif [ "${1:-}" != quiet ]; then
    echo "no mitmdump started by drive.sh"
  fi
}

# Flattens a uiautomator dump into "[l,t][r,b] 'text' 'content-desc'" lines.
flatten() {
  python3 -I - "$1" <<'PY'
import re, sys, html
xml = open(sys.argv[1], encoding="utf-8").read()
for node in re.findall(r"<node [^>]*>", xml):
    attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', node))
    text, desc = html.unescape(attrs.get("text", "")), html.unescape(attrs.get("content-desc", ""))
    if text or desc:
        print(f"{attrs.get('bounds', '')} '{text}'" + (f" desc='{desc}'" if desc else ""))
PY
}

cmd_dump() {
  [ $# -ge 1 ] || die "dump NAME"
  outdir
  "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null
  "$ADB" exec-out cat /sdcard/ui.xml > "$OUT/$1.xml"
  flatten "$OUT/$1.xml" > "$OUT/$1.txt"
  echo "$OUT/$1.txt ($(wc -l < "$OUT/$1.txt" | tr -d ' ') labelled nodes)"
}

# Prints "cx cy bounds 'text'" for every node whose text or content-desc contains $2, from dump file $1.
find_in() {
  python3 -I - "$1" "$2" <<'PY'
import re, sys, html
xml, needle = open(sys.argv[1], encoding="utf-8").read(), sys.argv[2].lower()
for node in re.findall(r"<node [^>]*>", xml):
    attrs = dict(re.findall(r'([\w-]+)="([^"]*)"', node))
    label = html.unescape(attrs.get("text", "")) or html.unescape(attrs.get("content-desc", ""))
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
    if m and needle in label.lower():
        l, t, r, b = map(int, m.groups())
        print((l + r) // 2, (t + b) // 2, attrs["bounds"], repr(label))
PY
}

cmd_find() {
  [ $# -ge 1 ] || die "find TEXT"
  outdir
  cmd_dump _find >/dev/null
  find_in "$OUT/_find.xml" "$1"
}

cmd_tap_text() {
  [ $# -ge 1 ] || die "tap-text TEXT"
  outdir
  cmd_dump _find >/dev/null
  local hit
  hit=$(find_in "$OUT/_find.xml" "$1" | head -1)
  [ -n "$hit" ] || die "no node with text '$1' (see $OUT/_find.txt)"
  set -- $hit
  "$ADB" shell input tap "$1" "$2"
  echo "tapped $1,$2 $3 ${*:4}"
}

cmd_shot() {
  [ $# -ge 1 ] || die "shot NAME"
  outdir
  "$ADB" exec-out screencap -p > "$OUT/$1.png"
  echo "$OUT/$1.png"
}

cmd_burst() {
  [ $# -ge 1 ] || die "burst NAME [N] [SEC]"
  local n="${2:-6}" gap="${3:-1}" i
  for i in $(seq 1 "$n"); do
    cmd_shot "$(printf '%s-%02d' "$1" "$i")"
    [ "$i" -lt "$n" ] && sleep "$gap"
  done
  return 0
}

app_pid() {
  local pid
  pid=$("$ADB" shell pidof -s "$APP_ID" 2>/dev/null | tr -d '\r' || true)
  [ -n "$pid" ] || die "$APP_ID is not running (drive.sh launch)"
  echo "$pid"
}

cmd_logcat() {
  local pid; pid=$(app_pid)
  if [ $# -ge 1 ]; then "$ADB" logcat -v time --pid="$pid" | grep --line-buffered -E "$1"
  else "$ADB" logcat -v time --pid="$pid"; fi
}

cmd_logcat_dump() {
  [ $# -ge 1 ] || die "logcat-dump NAME [PATTERN]"
  outdir
  local pid; pid=$(app_pid)
  "$ADB" logcat -d -v time --pid="$pid" | grep -E "${2:-.}" > "$OUT/$1.log" || true
  echo "$OUT/$1.log ($(wc -l < "$OUT/$1.log" | tr -d ' ') lines)"
}

main() {
  [ $# -ge 1 ] || { usage; exit 1; }
  local cmd="$1"; shift
  case "$cmd" in
    help|-h|--help) usage ;;
    boot) cmd_boot "$@" ;;
    install) ./gradlew :app:installDebug --console=plain ;;
    launch) "$ADB" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null && echo "launched $APP_ID" ;;
    clear-data) "$ADB" shell pm clear "$APP_ID" ;;
    proxy-on) "$ADB" shell settings put global http_proxy "${1:-$PROXY}" && echo "proxy ${1:-$PROXY}" ;;
    proxy-off) "$ADB" shell settings put global http_proxy :0 && echo "proxy cleared" ;;
    ca) cmd_ca ;;
    mitm) cmd_mitm "$@" ;;
    mitm-stop) cmd_mitm_stop ;;
    requests) grep -E '^\[[^]]+ [0-9]{2}:[0-9]{2}:[0-9]{2}\]' "$OUT/mitm.log" | grep -E "${1:-.}" || true ;;
    dump) cmd_dump "$@" ;;
    find) cmd_find "$@" ;;
    tap-text) cmd_tap_text "$@" ;;
    tap) [ $# -eq 2 ] || die "tap X Y"; "$ADB" shell input tap "$1" "$2" ;;
    swipe) [ $# -ge 4 ] || die "swipe X1 Y1 X2 Y2 [MS]"; "$ADB" shell input swipe "$@" ;;
    type) [ $# -ge 1 ] || die "type TEXT"; "$ADB" shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')" ;;
    key) [ $# -eq 1 ] || die "key KEYCODE"; "$ADB" shell input keyevent "$1" ;;
    shot) cmd_shot "$@" ;;
    burst) cmd_burst "$@" ;;
    logcat) cmd_logcat "$@" ;;
    logcat-dump) cmd_logcat_dump "$@" ;;
    *) usage; die "unknown command '$cmd'" ;;
  esac
}

main "$@"
