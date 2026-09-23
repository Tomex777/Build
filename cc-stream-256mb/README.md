# CC Stream — 256 MB profile

Minimal private WhatsApp companion for low-memory hosting.

## What it does

- Pairs by phone-number pairing code only (no QR UI).
- Uses Baileys Android companion mode.
- Automatically forwards incoming view-once media to the account's own chat (or OWNER_NUMBER).
- Keeps only a tiny in-memory dedupe set; it does not buffer/download media.
- `.ping` in the bot's own/owner DM reports latency, uptime, and RSS/heap RAM.

## Server setup

1. Use Node.js 24.
2. Copy `.env.example` to `.env` or configure BOT_NUMBER in your host's environment.
3. Run `npm install`.
4. Run `npm start`.
5. On first launch, copy the pairing code from the console into WhatsApp > Linked devices > Link with phone number instead.
6. Persist the `auth/` directory. If it is deleted, the account must be paired again.

The current Baileys Android companion mode is experimental. This project intentionally contains no economy, AI, games, downloaders, FFmpeg, Chromium, or command framework.
