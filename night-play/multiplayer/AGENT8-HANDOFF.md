# NIGHT Play Agent 8 — multiplayer handoff (2026-10-20)

## Scope and source
- Owner: Agent 8. Issue: https://github.com/Tomex777/Build/issues/398.
- Working branch: `feature/night-play-agent8-party-resilience-20261020`.
- Integration base: `feature/party-online-rooms-20261019` (PR #386). **Do not merge this branch into `main`: current NIGHT Play is not on `main`.**
- Source: `night-play/party/room/room.js`, `night-play/party/room/mobile-smoke.mjs`, `night-play/multiplayer/ttt/app.js`, `night-play/multiplayer/ttt/mobile-smoke.cjs`, `.github/workflows/night-play-online-multiplayer-qa.yml`.
- Related server PR: #387 (signed MSCC SQLite party rooms) and protected batch PR #390. These are **not part of this Agent 8 change**, and must not be deployed outside the authorized protected release.

## This iteration — implemented; awaiting integrated deployment verification
- Bound account-service requests to 12 seconds so disconnected mobile clients do not freeze indefinitely.
- Converted party-room polling to single-flight refresh with exponential backoff on backend outages. Prevents stale poll results from overwriting a completed vote or phase change.
- Preserve current room UI on temporary outages, provide a single connection-loss status and a recovery status on reconnect.
- Restart state sync when the page becomes visible again or is restored from browser back-forward cache. Never silently create a second hosted room following an uncertain create timeout.
- Initial invited-room reads retry automatically after temporary outages.
- Extended the independent-context five-round Chromium room test: disconnect one guest, verify voting controls stay in place, restore network, reload and resume that guest's room, check 320/360/390/412/768/1280-width layouts, capture extra screenshots. Then created a three-question personal quiz with no host name entry; two guests scored 3/3 and 0/3, with host display-name update propagated by the mocked server.
- Online Tic-Tac-Toe now stops polling while hidden, resumes after browser BFCache restoration and recovers automatically after network outages with bounded backoff and clearly described connection state.
- Extended Tic-Tac-Toe's two-independent-browser match test: real invite-link join between contexts, recover after simulated network outage, full legal X win/O loss, block post-win input, begin new match only after separate person joins, random matchmaking, and five viewports. Added a dedicated CI workflow for both online game flows without modifying coordinator-owned shared CI.

## Verification status
- Pure source parsing of `room.js` and `multiplayer/ttt/app.js`: passed when updating branch.
- **Dedicated multi-browser QA passed** on commit `27408b57e34a64e8dc5570cb226091b384aab6ff`, GitHub Actions run `39351088941` (both scripts completed). The 12 generated screenshots were downloaded and visually inspected; a mobile connection notice previously below the visible board was corrected and the fixed state is shown in the new artifact.
- **Full NIGHT Play Preview CI run `39351088961` FAILED on an unrelated Fruit Slice regression:** `night-play/qa/gameplay-gate.mjs:102` requires one real 320px touch crossing to score 10 but observed 0. Owner Agent 2 was notified via comment on merged Fruit PR #401. Agent 8 made no Fruit code edits. Multiplayer QA passed independently; combined-site CI and deployment remain blocked.
- Tests use a shared simulated authoritative backend in two isolated guest browser contexts. They are NOT proof of the production MSCC deployment or physical phones.
- Current Nomi production is separate: `Tomex777/Nomi` uses Supabase and Vercel, with its own `AGENTS.md` source-of-truth contract. **No Nomi prod deployment or account migration is included.**

## Remaining Agent 8 work / blockers
1. Two-browser party and TTT Playwright tests **passed** on commit `27408b57`; inspect the 12 saved images again after future integration, rerun both scripts against the actual combined deployed backend, and retest on physical phones.
2. Verify actual HTTPS signed `/api/account` to live MSCC with distinct verified WhatsApp accounts, guest cookies and real multi-device clients only after corresponding protected backend batch is released.
3. Implement durable co-op Jigsaw rooms with authoritative shared image/theme/difficulty, per-piece versioned moves, presence, resume and conflict-safe drag; sync real device clients and preserve solo mode.
4. Add online Chess and Draughts state adapters only after their engines expose an agreed legal-move/game-state interface with Agent 6. Do not edit Agent 6-owned game rules.
5. Cross-site Nomi identity federation is NOT implemented: avoid claiming Nomi sign-in == NIGHT WhatsApp session. Integrate approved game mechanics under NIGHT's existing canonical account gateway and explicit secure linking if cross-site credentials are required.
6. Agent 1/coordinator owns site-wide `AGENTS.md`, `docs/agent-backlog.md`, and merged preview. Do not overwrite its shared files from this branch.

## Immediate continuation
On `feature/night-play-agent8-party-resilience-20261020`, inspect `room.js`, `mobile-smoke.mjs`, and CI logs; run the two-guest test and examine screenshots; resolve issues. Then begin a separate co-op Jigsaw protocol + server contract module under `night-play/multiplayer/`; prove simultaneous remote dragging in two isolated Chromium contexts before UI catalog integration. Keep all items open until deployed integration is separately verified.
