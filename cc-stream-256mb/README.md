# CC Stream — 256 MB profile

Minimal private WhatsApp companion for low-memory hosting.

## Privacy / destination rule

The bot never reposts recovered view-once media into the source group or someone else's DM.

Every successful V1 recovery goes only to OWNER_NUMBER (defaults to the bot account itself / Message Yourself).

## Recovery paths

1. Live automatic stream: a newly received V1 is forwarded to the owner self-chat when Baileys exposes the live V1 payload.
2. Owner reply fallback: the owner can reply with any normal message to a V1; the bot silently tries to recover that quoted V1 to the owner self-chat.
3. Manual fallback: reply to a V1 with .cc; recovery goes to the owner self-chat.

If the same V1 was already delivered successfully, the small in-memory dedupe cache prevents duplicate delivery.

## Owner-only commands

- .cc — manual quoted V1 recovery
- .cc stream on
- .cc stream off
- .cc stream status
- .ping — latency, uptime and RAM
- .owner — owner status
- .help

Command responses are sent to the owner self-chat, not the source group/DM.

## Pairing

Pairing code only. No QR flow.

1. Use Node.js 24.
2. Set BOT_NUMBER to digits only including country code, e.g. 2348012345678.
3. OWNER_NUMBER is optional and defaults to BOT_NUMBER.
4. Run npm install.
5. Run npm start.
6. Copy the pairing code from the server console.
7. In WhatsApp: Linked devices > Link with phone number instead.
8. Keep the auth/ directory persistent across restarts/redeploys.

The bot uses Baileys Android companion mode and keeps no downloaded media buffers in RAM.
