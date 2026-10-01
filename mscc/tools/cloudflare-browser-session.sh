#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-status}"
TARGET_URL="${2:-https://animepahe.pw/}"
DISPLAY_NUM="${MSCC_CF_DISPLAY:-99}"
DISPLAY=":${DISPLAY_NUM}"
VNC_PORT="${MSCC_CF_VNC_PORT:-5900}"
NOVNC_PORT="${MSCC_CF_NOVNC_PORT:-6080}"
CDP_PORT="${MSCC_CF_CDP_PORT:-9222}"
TOR_HOST="${MSCC_TOR_HOST:-127.0.0.1}"
TOR_PORT="${MSCC_TOR_PORT:-9050}"
STATE_ROOT="${MSCC_SOURCE_SESSION_DIR:-/var/lib/mscc/source-sessions}"
PROFILE_ROOT="${MSCC_BROWSER_PROFILE_DIR:-/var/lib/mscc/browser-profiles}"
RUN_ROOT="${MSCC_BROWSER_RUN_DIR:-/tmp/mscc-browser-session}"

mkdir -p "$RUN_ROOT" "$STATE_ROOT" "$PROFILE_ROOT"
chmod 700 "$STATE_ROOT" "$PROFILE_ROOT" || true

find_chromium() {
  command -v chromium || command -v chromium-browser || command -v google-chrome || command -v google-chrome-stable
}

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing dependency: $1" >&2
    return 1
  }
}

alive() {
  local file="$1"
  [ -f "$file" ] || return 1
  local pid
  pid="$(cat "$file" 2>/dev/null || true)"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

stop_one() {
  local file="$1"
  if alive "$file"; then
    kill "$(cat "$file")" 2>/dev/null || true
    sleep .5
  fi
  rm -f "$file"
}

install_deps() {
  sudo apt-get update
  sudo apt-get install -y tor chromium xvfb x11vnc novnc websockify curl
  sudo systemctl enable --now tor
  echo "Installed Tor + temporary browser dependencies."
}

start_session() {
  need Xvfb
  need x11vnc
  need websockify
  need curl
  local chromium
  chromium="$(find_chromium || true)"
  [ -n "$chromium" ] || { echo "Chromium not found." >&2; exit 2; }

  if ! curl --silent --show-error --max-time 20       --proxy "socks5h://$TOR_HOST:$TOR_PORT"       https://check.torproject.org/api/ip | grep -q '"IsTor"[[:space:]]*:[[:space:]]*true'; then
    echo "Tor SOCKS proxy is not working at $TOR_HOST:$TOR_PORT." >&2
    exit 3
  fi

  local host
  host="$(node -e 'console.log(new URL(process.argv[1]).hostname)' "$TARGET_URL")"
  local profile="$PROFILE_ROOT/$host"
  mkdir -p "$profile"
  chmod 700 "$profile" || true

  stop_one "$RUN_ROOT/chromium.pid"
  stop_one "$RUN_ROOT/websockify.pid"
  stop_one "$RUN_ROOT/x11vnc.pid"
  stop_one "$RUN_ROOT/xvfb.pid"

  Xvfb "$DISPLAY" -screen 0 1280x800x24 -nolisten tcp >"$RUN_ROOT/xvfb.log" 2>&1 &
  echo $! >"$RUN_ROOT/xvfb.pid"
  sleep 1

  x11vnc -display "$DISPLAY" -localhost -rfbport "$VNC_PORT" -forever -shared -nopw     >"$RUN_ROOT/x11vnc.log" 2>&1 &
  echo $! >"$RUN_ROOT/x11vnc.pid"

  local novnc_web="/usr/share/novnc"
  [ -d "$novnc_web" ] || novnc_web="/usr/share/novnc/utils"
  websockify --web="$novnc_web" "127.0.0.1:$NOVNC_PORT" "127.0.0.1:$VNC_PORT"     >"$RUN_ROOT/websockify.log" 2>&1 &
  echo $! >"$RUN_ROOT/websockify.pid"

  DISPLAY="$DISPLAY" "$chromium"     --user-data-dir="$profile"     --proxy-server="socks5://$TOR_HOST:$TOR_PORT"     --host-resolver-rules="MAP * ~NOTFOUND , EXCLUDE localhost"     --remote-debugging-address=127.0.0.1     --remote-debugging-port="$CDP_PORT"     --no-first-run     --no-default-browser-check     --disable-dev-shm-usage     --disable-background-networking     --disable-sync     --window-size=1200,760     "$TARGET_URL"     >"$RUN_ROOT/chromium.log" 2>&1 &
  echo $! >"$RUN_ROOT/chromium.pid"

  for _ in $(seq 1 40); do
    if curl -fsS "http://127.0.0.1:$CDP_PORT/json/version" >/dev/null 2>&1; then
      break
    fi
    sleep .25
  done

  echo
  echo "Temporary Tor browser is running for: $TARGET_URL"
  echo "noVNC is bound to localhost only: 127.0.0.1:$NOVNC_PORT"
  echo
  echo "From your own device, create an SSH tunnel to the Azure VM:"
  echo "  ssh -L $NOVNC_PORT:127.0.0.1:$NOVNC_PORT <azure-user>@<azure-host>"
  echo
  echo "Then open in your browser:"
  echo "  http://127.0.0.1:$NOVNC_PORT/vnc.html"
  echo
  echo "Complete any interactive Cloudflare checkbox yourself."
  echo "After the page loads normally, capture the browser session with:"
  echo "  node tools/capture-browser-session.js $host"
  echo
  echo "Stop the temporary browser when finished:"
  echo "  bash tools/cloudflare-browser-session.sh stop"
}

status_session() {
  echo "Tor:"
  systemctl is-active tor 2>/dev/null || true
  echo "Processes:"
  for name in xvfb x11vnc websockify chromium; do
    if alive "$RUN_ROOT/$name.pid"; then
      echo "  $name: running (pid $(cat "$RUN_ROOT/$name.pid"))"
    else
      echo "  $name: stopped"
    fi
  done
  if command -v curl >/dev/null 2>&1; then
    echo "Tor exit:"
    curl --silent --show-error --max-time 15       --proxy "socks5h://$TOR_HOST:$TOR_PORT"       https://check.torproject.org/api/ip || true
    echo
  fi
}

stop_session() {
  stop_one "$RUN_ROOT/chromium.pid"
  stop_one "$RUN_ROOT/websockify.pid"
  stop_one "$RUN_ROOT/x11vnc.pid"
  stop_one "$RUN_ROOT/xvfb.pid"
  echo "Temporary browser stopped. Persistent browser profile and captured sessions were preserved."
}

case "$MODE" in
  install) install_deps ;;
  start) start_session ;;
  status) status_session ;;
  stop) stop_session ;;
  *)
    echo "Usage:"
    echo "  bash tools/cloudflare-browser-session.sh install"
    echo "  bash tools/cloudflare-browser-session.sh start https://animepahe.pw/"
    echo "  bash tools/cloudflare-browser-session.sh status"
    echo "  bash tools/cloudflare-browser-session.sh stop"
    exit 2
    ;;
esac
