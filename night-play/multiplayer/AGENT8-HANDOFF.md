# NIGHT Play Agent 8 — multiplayer handoff (2026-10-20)

## Scope and source
- Owner: Agent 8. Issue: https://github.com/Tomex777/Build/issues/398.
- Working branch: `feature/night-play-agent8-party-resilience-20261020`.
- Integration base: `feature/party-online-rooms-20261019` (PR #386). **Do not merge this branch into `main`: current NIGHT Play is not on `main`.**
- Source: `night-play/party/room/room.js`, `night-play/party/room/mobile-smoke.mjs`.
- Related server PR: #387 (signed MSCC SQLite party rooms) and protected batch PR #390. These are **not part of this Agent 8 change**, and must not be deployed outside the authorized protected release.

## This iteration — implemented, not yet accepted
- Bound account-service requests to 12 seconds so disconnected mobile clients do not freeze indefinitely.
- Converted party-room polling to single-flight refresh with exponential backoff on backend outages. Prevents stale poll results from overwriting a completed vote or phase change.
- Preserve current room UI on temporary outages, provide a single connection-loss status and a recovery status on reconnect.
- Restart state sync when the page becomes visible again or is restored from browser back-forward cache. Never silently create a second hosted room following an uncertain create timeout.
- Initial invited-room reads retry automatically after temporary outages.
- Extended the independent-context five-round Chromium room test: disconnect one guest, verify voting controls stay in place, restore network, reload and resume that guest's room, check 320/360/390/412/768/1280-width layouts, capture extra screenshots.

## Verification status
- Pure source parsing of `room.js`: passed when updating branch.
- Browser test edits: committed; **actual Chromium CI result pending**. Do not present this branch as tested/finished until `node night-play/party/room/mobile-smoke.mjs` executes successfully and generated visual outputs are inspected.
- Tests use a shared simulated authoritative backend in two isolated guest browser contexts. They are NOT proof of the production MSCC deployment or physical phones.
- Current Nomi production is separate: `Tomex777/Nomi` uses Supabase and Vercel, with its own `AGENTS.md` source-of-truth contract. **No Nomi prod deployment or account migration is included.**

## Remaining Agent 8 work / blockers
1. Review and rerun Playwright test `node night-play/party/room/mobile-smoke.mjs`; inspect disconnect/reconnect screenshots at narrow widths and fix failures.
2. Verify actual HTTPS signed `/api/account` to live MSCC with distinct verified WhatsApp accounts, guest cookies and real multi-device clients only after corresponding protected backend batch is released.
3. Implement durable co-op Jigsaw rooms with authoritative shared image/theme/difficulty, per-piece versioned moves, presence, resume and conflict-safe drag; sync real device clients and preserve solo mode.
4. Add online Chess and Draughts state adapters only after their engines expose an agreed legal-move/game-state interface with Agent 6. Do not edit Agent 6-owned game rules.
5. Cross-site Nomi identity federation is NOT implemented: avoid claiming Nomi sign-in == NIGHT WhatsApp session. Integrate approved game mechanics under NIGHT's existing canonical account gateway and explicit secure linking if cross-site credentials are required.
6. Agent 1/coordinator owns site-wide `AGENTS.md`, `docs/agent-backlog.md`, and merged preview. Do not overwrite its shared files from this branch.

## Immediate continuation
On `feature/night-play-agent8-party-resilience-20261020`, inspect `room.js`, `mobile-smoke.mjs`, and CI logs; run the two-guest test and examine screenshots; resolve issues. Then begin a separate co-op Jigsaw protocol + server contract module under `night-play/multiplayer/`; prove simultaneous remote dragging in two isolated Chromium contexts before UI catalog integration. Keep all items open until deployed integration is separately verified.
