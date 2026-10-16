# NIGHT Play account connection

The existing NIGHT Play combined site connects to the existing MSCC process and
SQLite database. This release adds account identity only. Device game saves,
unranked scores, existing WhatsApp games and cross-platform matches are separate.

1. Open https://night-play-combined-preview.vercel.app and choose Connect WhatsApp.
2. Copy the generated `.minigames link XXXX-XXXX-XXXX` command.
3. Send it privately to any NIGHT account. Group redemption is rejected.
4. The same browser signs in automatically. A second browser can link to the same
   profile through another fresh code. Codes expire after five minutes and are
   single-use. Creating a replacement code invalidates the previous one.

The connection menu disconnects the current browser or removes the website
account. `.minigames unlink` privately signs out all website browsers, preserving
the profile for a later connection. `.minigames profile` shows its display name.

## Deployment contract

The website's server-only Vercel configuration is:

- `NIGHT_PLAY_ORIGIN`: the exact deployed website origin.
- `NIGHT_PLAY_MSCC_URL`: the existing certified Azure HTTPS origin.
- `NIGHT_PLAY_SIGNING_KEY`: a Secret containing the Ed25519 PKCS8 private PEM.

Only its corresponding public key is committed as `night-play-public-key.pem`.
Never copy the private key, an admin credential or WhatsApp credentials into
source, client JavaScript, API results, test fixtures or deployment logs. Key
rotation requires staging the matching public key and updating the Vercel
Secret together. Other deployments must not reuse this site's private key.

Vercel forwards only allowlisted account actions to `/api/night-play/rpc`, signed
over the method, exact path, timestamp, random nonce and body hash. MSCC rejects
old, tampered, unsigned and replayed requests. Browser session tokens are random
256-bit values in a `__Host-` HttpOnly/Secure/SameSite=Strict cookie; the database
stores hashes. Browser mutations require the exact website Origin. Authentication
never uses a browser-submitted phone/JID/LID; the bot supplies its verified sender
and verified aliases. Opaque LIDs are never interpreted as phone numbers.

The existing Azure installer adds one exact nginx location to the existing
Cortex HTTPS site, forwarding only the signed RPC to loopback `127.0.0.1:8787`.
It validates nginx, verifies the TLS route rejects unsigned requests with the
NIGHT Play response, and rechecks existing Cortex authentication. It restores
the original nginx configuration on failure. No control port is made public.
The existing installer continues to roll MSCC back if activation fails.

`play_*` tables live in the existing `mscc-shared.sqlite`, with cascading removal
of identity mappings, sessions and challenges. Expired challenges, sessions,
rate counters and signature nonces are cleaned regularly. Profiles persist until
removed. Browser APIs expose only a random profile ID and display name. The bot
does not send unsolicited WhatsApp messages as part of this flow.

## Verification

`node night-play-selftest.js` checks the real SQLite store, bot command and signed
HTTP handler, including PN/LID continuity, multiple browsers, expiry, reuse,
session revocation, removal, rate limits, body tampering and replay. The website
has its own gateway and mobile/desktop account flow tests. A deployment approval
alone is not proof that a real WhatsApp login has succeeded; test that final
private command after the protected Azure release is live.
