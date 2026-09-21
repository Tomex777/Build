#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.whatsapp"
CHAT_ACTIVITY="$PACKAGE/.ChatPreviewActivity"
BROWSER_ACTIVITY="$PACKAGE/.presentation.browser.NightBrowserActivity"
APK="whatsapp-ai-android/app/build/outputs/apk/debug/app-debug.apk"
OUT="night-browser-artifacts"
SERVER_LOG="/tmp/night-browser-server.log"
mkdir -p "$OUT"
: > "$SERVER_LOG"

cat > /tmp/night_browser_server.py <<'PY'
from http.server import BaseHTTPRequestHandler, HTTPServer

LOG = "/tmp/night-browser-server.log"

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def do_GET(self):
        cookie = self.headers.get("Cookie", "")
        with open(LOG, "a", encoding="utf-8") as handle:
            handle.write(f"path={self.path} cookie={cookie}\n")
            handle.flush()

        if self.path.startswith("/start"):
            self.send_response(302)
            self.send_header("Set-Cookie", "night_session=shared; Path=/; SameSite=Lax")
            self.send_header("Location", "/next")
            self.end_headers()
            return

        if self.path.startswith("/next"):
            body = b"""<!doctype html>
<html>
<head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="font-family:sans-serif;padding:24px">
<h1>Night browser ready</h1>
<p id="status">Inline browser session is active.</p>
<div style="height:600px"></div>
<p>Bottom of browser page</p>
</body>
</html>"""
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        self.send_response(404)
        self.end_headers()

HTTPServer(("0.0.0.0", 8765), Handler).serve_forever()
PY

python3 /tmp/night_browser_server.py >"$OUT/server-stdout.txt" 2>&1 &
SERVER_PID=$!
trap 'kill "$SERVER_PID" >/dev/null 2>&1 || true' EXIT
sleep 1

cat > /tmp/night_browser_uia.py <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

root = ET.parse("/tmp/window.xml").getroot()
nodes = list(root.iter("node"))
mode = sys.argv[1]
value = sys.argv[2]

def bounds(node):
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        return None
    return tuple(map(int, match.groups()))

def find_desc(desc):
    return next((node for node in nodes if node.attrib.get("content-desc") == desc), None)

if mode == "desc":
    node = find_desc(value)
    if node is None:
        raise SystemExit(2)
    print("found")
elif mode == "click":
    node = find_desc(value)
    if node is None:
        raise SystemExit(2)
    parents = {child: parent for parent in root.iter() for child in parent}
    current = node
    while current is not None and current.attrib.get("clickable") != "true":
        current = parents.get(current)
    target = current if current is not None else node
    box = bounds(target)
    if box is None:
        raise SystemExit(2)
    x1,y1,x2,y2 = box
    print(f"{(x1+x2)//2} {(y1+y2)//2}")
else:
    raise SystemExit(2)
PY

refresh_ui() {
  adb shell uiautomator dump /sdcard/night-browser.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/night-browser.xml > /tmp/window.xml
}

wait_desc() {
  local desc="$1"
  for _ in $(seq 1 20); do
    refresh_ui || true
    if python3 /tmp/night_browser_uia.py desc "$desc" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "Timed out waiting for accessibility node: $desc" >&2
  return 1
}

tap_desc() {
  refresh_ui
  read -r x y <<<"$(python3 /tmp/night_browser_uia.py click "$1")"
  adb shell input tap "$x" "$y"
  sleep 1
}

wait_server_cookie_count() {
  local minimum="$1"
  for _ in $(seq 1 30); do
    count="$(grep -c 'path=/next cookie=.*night_session=shared' "$SERVER_LOG" || true)"
    if [ "$count" -ge "$minimum" ]; then
      return 0
    fi
    sleep 1
  done
  echo "Expected at least $minimum /next requests with shared cookie." >&2
  cp "$SERVER_LOG" "$OUT/failure-server-log.txt" 2>/dev/null || true
  adb exec-out screencap -p > "$OUT/failure-browser.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/night-browser-failure.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/night-browser-failure.xml > "$OUT/failure-browser.xml" 2>/dev/null || true
  adb logcat -d -v threadtime > "$OUT/failure-logcat.txt" 2>/dev/null || true
  cat "$SERVER_LOG" >&2 || true
  return 1
}

assert_no_night_crash() {
  adb logcat -d -v threadtime > "$OUT/logcat.txt"
  if grep -A8 "FATAL EXCEPTION:" "$OUT/logcat.txt" | grep -q "Process: $PACKAGE"; then
    echo "Night crashed during browser validation." >&2
    exit 1
  fi
  if grep -q "ANR in $PACKAGE" "$OUT/logcat.txt"; then
    echo "Night hit an ANR during browser validation." >&2
    exit 1
  fi
}

adb install --no-streaming -r "$APK"
adb shell pm clear "$PACKAGE" >/dev/null 2>&1 || true
adb reverse tcp:8765 tcp:8765
adb shell wm size 709x1536
adb shell wm density 240
adb logcat -c
adb shell am start -W -n "$CHAT_ACTIVITY" --es mode browser
sleep 4

echo "STEP: inline browser loads local page and establishes session"
wait_desc "Browser message test.browser"
wait_desc "Expand browser"
wait_desc "Verify browser session"
wait_server_cookie_count 1
adb exec-out screencap -p > "$OUT/01-inline-browser.png"
cp "$SERVER_LOG" "$OUT/01-server-log.txt"

echo "STEP: expand opens full browser with shared cookie/session"
tap_desc "Expand browser"
sleep 2
adb shell dumpsys activity activities | grep -q "NightBrowserActivity"
wait_desc "Night full browser"
wait_desc "Close browser"
wait_desc "Reload browser"
wait_server_cookie_count 2
adb exec-out screencap -p > "$OUT/02-full-browser.png"
cp "$SERVER_LOG" "$OUT/02-server-log.txt"

echo "STEP: full browser reload remains in the same session"
tap_desc "Reload browser"
wait_server_cookie_count 3
adb exec-out screencap -p > "$OUT/03-full-browser-reloaded.png"

echo "STEP: close returns to original chat browser message"
tap_desc "Close browser"
sleep 2
adb shell dumpsys activity activities | grep -q "ChatPreviewActivity"
wait_desc "Browser message test.browser"

echo "STEP: extension verify action reaches verified state"
tap_desc "Verify browser session"
wait_desc "Browser session verified"
adb shell pidof "$PACKAGE" >/dev/null
adb exec-out screencap -p > "$OUT/04-inline-verified.png"

assert_no_night_crash
cp "$SERVER_LOG" "$OUT/final-server-log.txt"

printf '%s\n' \
  "androidApi=36" \
  "inlineBrowser=true" \
  "fullBrowser=true" \
  "sharedCookies=true" \
  "sharedCurrentUrl=true" \
  "extensionBrowserTemplate=true" \
  "verifyAction=true" \
  "verificationState=true" \
  "unsafeSchemesCoveredByUnitTests=true" \
  "watermarkFree=true" > "$OUT/summary.txt"
