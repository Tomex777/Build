# Homira TURN

Homira now fetches TURN ICE configuration from the authenticated Supabase Edge Function `turn-credentials` before creating a WebRTC peer connection.

The Android client receives only temporary TURN credentials. It never receives the long-lived private Coturn shared secret.

## Development fallback

Until a private Homira Coturn host is configured, the Edge Function uses Metered Open Relay's documented development service. This gives Homira a real TURN relay path now so relay-only call testing can begin.

Before a production release, configure a private Homira TURN host and remove the public development fallback.

## Private Coturn server

Run Coturn on a Linux VM with a public IPv4 address:

```bash
cp .env.example .env
# Set TURN_REALM, TURN_EXTERNAL_IP, and a long random TURN_SHARED_SECRET.
docker compose up -d
```

Open these inbound firewall rules:

- UDP 3478
- TCP 3478
- UDP 49160-49200

The current VM template provides TURN over UDP/TCP 3478 and deliberately disables TLS/DTLS until a real hostname and certificate are attached.

## Supabase secrets for private TURN

Set these on the Homira Supabase project:

```text
HOMIRA_TURN_SHARED_SECRET=<same shared secret configured in Coturn>
HOMIRA_TURN_URLS=["turn:turn.example.com:3478","turn:turn.example.com:3478?transport=tcp"]
HOMIRA_TURN_TTL_SECONDS=3600
```

The Edge Function generates Coturn TURN REST credentials in the standard `expiry:user-id` form and signs them with HMAC-SHA1.

## Relay-only app validation

For a diagnostic Android build, set:

```text
HOMIRA_FORCE_TURN_RELAY=true
```

That makes WebRTC use `PeerConnection.IceTransportsType.RELAY`. If the call reaches `Connected`, host and STUN-only candidates cannot have carried the media path.


## Development relay transport note

The development fallback uses plain TURN on port 80 (UDP/TCP) and TURNS over TCP on port 443. The CI allocation probe intentionally exercises plain TURN/TCP on port 80 so it does not confuse raw TCP with TLS.
