# Homira TURN

Homira fetches authenticated ICE configuration from the Supabase Edge Function `turn-credentials` before creating each WebRTC peer connection. Long-lived provider secrets never go into the Android APK.

## Recommended managed provider: Cloudflare Realtime TURN

Set these Supabase Edge Function secrets:

```text
HOMIRA_CLOUDFLARE_TURN_KEY_ID=<Cloudflare TURN key ID>
HOMIRA_CLOUDFLARE_TURN_API_TOKEN=<token belonging to that TURN key>
HOMIRA_TURN_TTL_SECONDS=86400
```

The Edge Function calls Cloudflare's `generate-ice-servers` endpoint and returns only the short-lived ICE username/credential to Homira. Port 53 URLs are filtered because browsers may block them; Homira retains UDP/TCP/TLS relay URLs on the standard Cloudflare ports.

## Self-hosted alternative: Coturn

The checked-in `docker-compose.yml` can run Homira's own Coturn server on a Linux VM with a public IPv4 address.

```bash
cp .env.example .env
# Set TURN_REALM, TURN_EXTERNAL_IP, and a long random TURN_SHARED_SECRET.
docker compose up -d
```

Open:

- UDP 3478
- TCP 3478
- UDP 49160-49200

Then set these Supabase secrets:

```text
HOMIRA_TURN_SHARED_SECRET=<same secret as Coturn>
HOMIRA_TURN_URLS=["turn:turn.example.com:3478","turn:turn.example.com:3478?transport=tcp"]
HOMIRA_TURN_TTL_SECONDS=86400
```

The Edge Function mints standard Coturn TURN-REST credentials in `expiry:user-id` form with HMAC-SHA1.

## No-provider behavior

If neither Cloudflare nor private Coturn is configured, the Edge Function returns `configured: false` and only STUN. It deliberately does not inject an unverified public TURN server.

## Relay-only Android validation

Build with:

```text
HOMIRA_FORCE_TURN_RELAY=true
```

The dedicated `Homira TURN Relay Android` workflow does this automatically. In that APK, WebRTC uses `PeerConnection.IceTransportsType.RELAY`. A successful connected call therefore proves the media path has TURN relay candidates available.


The default TURN credential TTL is 24 hours. This is intentionally longer than a normal Homira call so long-running calls do not lose the ability to refresh TURN allocations mid-session.
