#!/usr/bin/env bash
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

STACK_DIR="${MSCC_SOURCE_STACK_DIR:-/opt/mscc-source-stack}"
NETWORK="${MSCC_SOURCE_DOCKER_NETWORK:-mscc-source-net}"
TOR_IMAGE="${MSCC_TOR_IMAGE:-mscc-tor:local}"
TOR_CONTAINER="${MSCC_TOR_CONTAINER:-mscc-tor}"
TOR_PORT="${MSCC_TOR_PORT:-9050}"
FLARE_CONTAINER="${MSCC_FLARE_CONTAINER:-mscc-flaresolverr}"
FLARE_IMAGE="${MSCC_FLARE_IMAGE:-ghcr.io/flaresolverr/flaresolverr:latest}"
FLARE_PORT="${MSCC_FLARE_PORT:-8191}"
PYTHON_VENV="${MSCC_SOURCE_PYTHON_VENV:-$STACK_DIR/venv}"

echo "=== MSCC source browser stack ==="
echo "Installing Tor/FlareSolverr prerequisites and reusing Docker when already present..."

sudo apt-get update
PACKAGES=(curl jq git python3 python3-venv python3-pip ffmpeg ca-certificates)
if ! command -v docker >/dev/null 2>&1; then
  PACKAGES+=(docker.io)
fi
sudo apt-get install -y "${PACKAGES[@]}"

if ! sudo docker info >/dev/null 2>&1; then
  sudo systemctl enable --now docker
fi

sudo mkdir -p "$STACK_DIR/tor"
sudo tee "$STACK_DIR/tor/Dockerfile" >/dev/null <<'EOF'
FROM debian:bookworm-slim
RUN apt-get update  && apt-get install -y --no-install-recommends tor ca-certificates curl  && rm -rf /var/lib/apt/lists/*
COPY torrc /etc/tor/torrc
CMD ["tor","-f","/etc/tor/torrc"]
EOF

sudo tee "$STACK_DIR/tor/torrc" >/dev/null <<'EOF'
SocksPort 0.0.0.0:9050
Log notice stdout
ClientOnly 1
AvoidDiskWrites 1
EOF

echo ">>> Building local Tor image..."
sudo docker build -t "$TOR_IMAGE" "$STACK_DIR/tor"

if ! sudo docker network inspect "$NETWORK" >/dev/null 2>&1; then
  sudo docker network create "$NETWORK" >/dev/null
fi

sudo docker rm -f "$TOR_CONTAINER" >/dev/null 2>&1 || true
sudo docker run -d   --name "$TOR_CONTAINER"   --network "$NETWORK"   -p "127.0.0.1:$TOR_PORT:9050"   --restart unless-stopped   "$TOR_IMAGE" >/dev/null

echo ">>> Waiting for Tor bootstrap..."
TOR_READY=0
for _ in $(seq 1 90); do
  if sudo docker logs "$TOR_CONTAINER" 2>&1 | grep -q "Bootstrapped 100%"; then
    TOR_READY=1
    break
  fi
  sleep 1
done
if [ "$TOR_READY" -ne 1 ]; then
  echo "Tor did not bootstrap." >&2
  sudo docker logs "$TOR_CONTAINER" --tail 100 >&2 || true
  exit 1
fi

echo ">>> Verifying Tor exit..."
TOR_JSON="$(sudo docker exec "$TOR_CONTAINER"   curl -fsS --max-time 30   --proxy socks5h://127.0.0.1:9050   https://check.torproject.org/api/ip)"
echo "$TOR_JSON" | jq .
echo "$TOR_JSON" | jq -e '.IsTor == true' >/dev/null

echo ">>> Installing curl_cffi helper runtime..."
sudo mkdir -p "$STACK_DIR"
sudo chown -R "$USER:$USER" "$STACK_DIR"
if [ ! -x "$PYTHON_VENV/bin/python" ]; then
  python3 -m venv "$PYTHON_VENV"
fi
"$PYTHON_VENV/bin/python" -m pip install --upgrade pip >/dev/null
"$PYTHON_VENV/bin/python" -m pip install --upgrade curl_cffi >/dev/null
"$PYTHON_VENV/bin/python" - <<'PY'
import curl_cffi
print("curl_cffi: ready")
PY

echo ">>> Starting FlareSolverr on localhost only..."
sudo docker pull "$FLARE_IMAGE"
sudo docker rm -f "$FLARE_CONTAINER" >/dev/null 2>&1 || true
sudo docker run -d   --name "$FLARE_CONTAINER"   --network host   --shm-size=512m   -e LOG_LEVEL=info   -e LOG_HTML=false   -e HEADLESS=true   -e DISABLE_MEDIA=true   -e BROWSER_WAIT_TIMEOUT=5   -e BROWSER_TIMEOUT=120000   -e "PROXY_URL=socks5://127.0.0.1:$TOR_PORT"   -e TZ=Etc/UTC   --restart unless-stopped   "$FLARE_IMAGE" >/dev/null

echo ">>> Waiting for FlareSolverr..."
FLARE_READY=0
for _ in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:$FLARE_PORT/health" >/tmp/mscc-flaresolverr-health.json 2>/dev/null; then
    FLARE_READY=1
    break
  fi
  sleep 1
done
if [ "$FLARE_READY" -ne 1 ]; then
  echo "FlareSolverr did not become healthy." >&2
  sudo docker logs "$FLARE_CONTAINER" --tail 120 >&2 || true
  exit 1
fi

echo
echo "FlareSolverr health:"
cat /tmp/mscc-flaresolverr-health.json | jq . || cat /tmp/mscc-flaresolverr-health.json

echo
echo "=== READY ==="
echo "Tor container:        $TOR_CONTAINER"
echo "Tor SOCKS proxy:      socks5h://127.0.0.1:$TOR_PORT"
echo "FlareSolverr:         http://127.0.0.1:$FLARE_PORT"
echo "FlareSolverr proxy:   socks5://127.0.0.1:$TOR_PORT"
echo "curl_cffi Python:     $PYTHON_VENV/bin/python"
echo "3" > "$STACK_DIR/.animepahe-stack-version"
echo "FlareSolverr is bound only to localhost."
echo
echo "Recommended /etc/mscc.env values:"
echo "  MSCC_FLARESOLVERR_URL=http://127.0.0.1:$FLARE_PORT"
echo "  MSCC_TOR_PROXY=socks5h://127.0.0.1:$TOR_PORT"
echo "  MSCC_ANIMEPAHE_FLARE_PROXY=socks5://127.0.0.1:$TOR_PORT"
echo "  MSCC_CURL_CFFI_PYTHON=$PYTHON_VENV/bin/python"
echo
echo "Next:"
echo "  bash /opt/mscc/current/tools/test-animepahe-azure.sh Bleach"
echo "For one real HLS -> MP4 episode test:"
echo "  bash /opt/mscc/current/tools/test-animepahe-azure.sh Bleach --download-one"
