#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
  echo "Run this installer as root." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PUBLIC_IP="${1:-${CORTEX_PUBLIC_IP:-}}"

if [ -z "$PUBLIC_IP" ]; then
  PUBLIC_IP="$(
    curl -fsS --connect-timeout 3 --max-time 8 -H Metadata:true       'http://169.254.169.254/metadata/instance/network/interface?api-version=2021-02-01'     | python3 -c 'import json,sys; d=json.load(sys.stdin); interfaces=d if isinstance(d,list) else d.get("interface", d.get("network",{}).get("interface",[])); print(next((str(x.get("publicIpAddress","")).strip() for i in interfaces if isinstance(i,dict) for x in i.get("ipv4",{}).get("ipAddress",[]) if isinstance(x,dict) and str(x.get("publicIpAddress","")).strip()), ""))'     || true
  )"
fi

if [ -n "$PUBLIC_IP" ]; then
  echo "Detected Azure public IP from IMDS: $PUBLIC_IP"
else
  echo "Azure IMDS did not expose a public IPv4; checking the VM's externally visible IPv4..."
  PUBLIC_IP="$(curl -4fsS --connect-timeout 3 --max-time 8 https://api.ipify.org || true)"
  if [ -n "$PUBLIC_IP" ]; then
    echo "Externally visible IPv4 candidate: $PUBLIC_IP"
    echo "NOTE: Verify this matches the Static Public IP attached to the Azure VM/NIC."
  fi
fi

python3 - "$PUBLIC_IP" <<'PY'
import ipaddress, sys
raw = sys.argv[1].strip()
try:
    value = ipaddress.ip_address(raw)
except ValueError:
    raise SystemExit("A valid Azure static public IP is required.")
if value.version != 4:
    raise SystemExit("This helper currently supports Azure static public IPv4 addresses.")
print(value)
PY

if [ -z "$PUBLIC_IP" ]; then
  echo "Could not determine a public IPv4 automatically." >&2
  echo "Run again with: sudo bash $0 <AZURE_STATIC_PUBLIC_IPV4>" >&2
  exit 1
fi

if ! systemctl cat cortex-agent.service >/dev/null 2>&1; then
  echo "Cortex Agent is not installed yet; installing it first..."
  bash "$SCRIPT_DIR/install.sh"
fi

systemctl enable --now cortex-agent.service >/dev/null
if ! curl -fsS -o /dev/null -H "Authorization: Bearer $(sed -n 's/^CORTEX_AGENT_TOKEN=//p' /etc/cortex-agent.env | tail -1)"   http://127.0.0.1:47831/api/cortex/host/status; then
  echo "Cortex Agent is not healthy on 127.0.0.1:47831." >&2
  exit 1
fi

# Keep the public HTTPS ingress transfer ceiling aligned with the Agent. Without
# this, nginx can reject a valid streamed upload before Cortex sees it.
MAX_TRANSFER_BYTES="$(sed -n 's/^CORTEX_MAX_TRANSFER_BYTES=//p' /etc/cortex-agent.env | tail -1)"
if ! [[ "$MAX_TRANSFER_BYTES" =~ ^[0-9]+$ ]] || [ "$MAX_TRANSFER_BYTES" -lt 10485760 ]; then
  MAX_TRANSFER_BYTES=536870912
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y nginx python3-venv ca-certificates curl

CERTBOT_VENV=/opt/cortex-certbot
if [ ! -x "$CERTBOT_VENV/bin/certbot" ]; then
  python3 -m venv "$CERTBOT_VENV"
fi
"$CERTBOT_VENV/bin/pip" install --disable-pip-version-check --quiet --upgrade pip
"$CERTBOT_VENV/bin/pip" install --disable-pip-version-check --quiet 'certbot>=5.4'
ln -sfn "$CERTBOT_VENV/bin/certbot" /usr/local/bin/certbot

WEBROOT=/var/www/cortex-acme
SITE=/etc/nginx/sites-available/cortex-agent
LINK=/etc/nginx/sites-enabled/cortex-agent
install -d -m 0755 "$WEBROOT/.well-known/acme-challenge"

cat >"$SITE" <<EOF
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    location ^~ /.well-known/acme-challenge/ {
        root $WEBROOT;
        default_type text/plain;
        try_files \$uri =404;
    }

    location / {
        return 404;
    }
}
EOF

rm -f /etc/nginx/sites-enabled/default
ln -sfn "$SITE" "$LINK"
nginx -t
systemctl enable --now nginx
systemctl reload nginx

CERTBOT_ARGS=(
  certonly
  --preferred-profile shortlived
  --webroot
  --webroot-path "$WEBROOT"
  --ip-address "$PUBLIC_IP"
  --non-interactive
  --agree-tos
)

if [ -n "${CORTEX_ACME_EMAIL:-}" ]; then
  CERTBOT_ARGS+=(--email "$CORTEX_ACME_EMAIL" --no-eff-email)
else
  CERTBOT_ARGS+=(--register-unsafely-without-email)
fi

certbot "${CERTBOT_ARGS[@]}"

CERT_DIR="/etc/letsencrypt/live/$PUBLIC_IP"
test -s "$CERT_DIR/fullchain.pem"
test -s "$CERT_DIR/privkey.pem"

cat >"$SITE" <<EOF
server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    location ^~ /.well-known/acme-challenge/ {
        root $WEBROOT;
        default_type text/plain;
        try_files \$uri =404;
    }

    location / {
        return 404;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name $PUBLIC_IP;

    ssl_certificate $CERT_DIR/fullchain.pem;
    ssl_certificate_key $CERT_DIR/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:CORTEXTLS:10m;
    ssl_session_timeout 1d;

    client_max_body_size $MAX_TRANSFER_BYTES;

    location / {
        proxy_pass http://127.0.0.1:47831;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_buffering off;
        proxy_request_buffering off;
        proxy_read_timeout 600s;
        proxy_send_timeout 600s;
    }
}
EOF

nginx -t
systemctl reload nginx

cat >/etc/systemd/system/cortex-ip-cert-renew.service <<EOF
[Unit]
Description=Renew Cortex public-IP TLS certificate
After=network-online.target nginx.service
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/certbot renew --quiet --deploy-hook "/usr/bin/systemctl reload nginx"
EOF

cat >/etc/systemd/system/cortex-ip-cert-renew.timer <<'EOF'
[Unit]
Description=Check Cortex public-IP TLS certificate twice daily

[Timer]
OnCalendar=*-*-* 00,12:17:00
RandomizedDelaySec=1800
Persistent=true

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now cortex-ip-cert-renew.timer

TOKEN="$(sed -n 's/^CORTEX_AGENT_TOKEN=//p' /etc/cortex-agent.env | tail -1)"
if [ -z "$TOKEN" ]; then
  echo "CORTEX_AGENT_TOKEN is missing from /etc/cortex-agent.env." >&2
  exit 1
fi

curl --resolve "$PUBLIC_IP:443:127.0.0.1" -fsS   -H "Authorization: Bearer $TOKEN"   "https://$PUBLIC_IP/api/cortex/host/status" >/dev/null

echo
echo "=== CORTEX DOMAINLESS HTTPS READY ==="
echo "Cortex Agent URL: https://$PUBLIC_IP"
echo "Agent origin remains private: http://127.0.0.1:47831"
echo "MSCC control bridge should remain private: http://127.0.0.1:8788"
echo "Certificate renewal timer: cortex-ip-cert-renew.timer"
echo
echo "Azure NSG must allow inbound TCP 80 (ACME renewal) and 443 (Cortex)."
echo "Do NOT expose TCP 47831, 8787, or 8788."
echo "Keep this Azure public IP static; changing it requires a new IP certificate."
