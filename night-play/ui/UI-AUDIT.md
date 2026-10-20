# NIGHT Play — Agent 9 UI/UX audit
**Date:** 2026-10-20  
**Source audited:** `Tomex777/Build`, branch `feature/party-online-rooms-20261019`  
**Agent branch:** `agent9/night-play-site-ux-20261020`  
**Scope:** Shared NIGHT Play website shell only. No changes to frozen Channels visuals or gameplay engines.

## Confirmed from source / addressed in this PR

| Priority | Finding | Evidence | Resolution / verification |
| --- | --- | --- | --- |
| P1 | Sticky global site header has `z-index:60`, while the fixed legacy `.game-page` has `z-index:20`. Opening 2048/Snake/Tic-Tac-Toe leaves the site header above the game-specific controls. | `site-shell.css` and `index.html` game-page CSS/JS. `gameDialog.showModal()` applies `body.in-game`. | Shared site header and bottom tabs are hidden only during `body.in-game`, without reflow. A Chromium test now checks game header/back/settings and returns to site after exiting. |
| P2 | Shared desktop menu links and site logo do not guarantee a minimum 44px interaction height. | `site-shell.css` nav rules lacked minimum sizes. | Applied 44px minimum heights and visible keyboard focus. |
| P2 | Legacy fullscreen 2048 game displayed both an upper-left **All games** button and a second **Exit** button below the board; on a real 320px Chromium screenshot, Exit wrapped into its own row and the top back label also wrapped. | Screenshot artifact from successful UI run 39349675685, `game-2048-320.png`. | Hide only the duplicate bottom Exit in shared CSS; preserve top back. Reduce narrow-phone top bar gaps and require a single-line back label; screenshot retest pending. |
| P2 | At narrow tablet widths the logo, top menu and WhatsApp action compete for horizontal space. | `site-shell.css` 601–900px layout and inline site header markup. | Reduced spacing, badge and type density at 601–760px without removing primary destinations. Screenshots cover 640/760px. |

## Follow-up audit — not silently modified

| Priority | Observation | Owner / next action |
| --- | --- | --- |
| P1 | `night-play/index.html` includes apparent literal backslash-n prefixes in the first inline CSS rule (before `.night-newgame-thumb`). Those can change CSS selector matching if they are literal. | Agent 1 shared markup owner: inspect computed cover rules in browser, remove malformed escapes only if confirmed. |
| P1 | Full NIGHT Play profile is meant to use canonical WhatsApp-linked identity, but `#playerNameForm` still displays `Saved on this device.`; ensure this text matches real linked-name propagation. | Agent 8 identity / Agent 1 UI integration. Test linked and guest flows end-to-end. |
| P2 | `night-play/DESIGN.md` describes only three playable prototype games and says account linking is inactive despite multiple current implemented game routes and active linked-account work. | Agent 1 documentation owner: reconcile with true current state. |
| P2 | `/terms` currently says competitive multiplayer is unavailable, while library advertises online party games. Verify what is genuinely live, then make policy wording precise. | Agent 8 backend truth / Agent 1 legal copy review. |
| P2 | Gallery includes network-dependent photo backgrounds (e.g., Jigsaw cover served by Unsplash) without locally verified fallback. | Agent 5 Jigsaw / Agent 1 catalogue: test image failure and responsive fallback; use rights-cleared local art where appropriate. |
| P2 | One large `index.html` bundles much legacy CSS and inline JavaScript. Shared markup changes risk cascade regressions during concurrent agent work. | Agent 1 integration: coordinate ownership; avoid concurrent edits. Incremental extraction only after screenshot parity. |

## Verification and signoff

- `night-play/ui/site-chrome-smoke.mjs` launches real Chromium against the actual `night-play/index.html` served locally, exercises route transitions and 2048 enter/exit, and captures PNGs at 320/360/390/412/640/760/1280 widths.
- Workflow `.github/workflows/night-play-agent9-ui.yml` runs tests for this PR and uploads the screenshots.
- **Do not mark finished until**: CI reaches successful terminal result, screenshots show no critical clipping, site header is hidden during the game, game controls remain usable, and the coordinator verifies integration into the combined deployed preview.
- Continue audit in next Agent 9 session: profile connection, game-library filtering/search and card keyboard semantics; inspect full-page screenshots for overflow at narrow widths; report reproducible issues to each owning agent rather than editing their files.

## Agent 9 continuity — 20 October 2026

- **Owner:** Agent 9 UI/UX. **PR:** [#413](https://github.com/Tomex777/Build/pull/413), remains draft until newest CI/browser artifacts are reviewed. **Branch:** `agent9/night-play-site-ux-20261020` from `feature/party-online-rooms-20261019`.
- **Last verified source revision:** `e99e430`; full NIGHT Play Preview CI and dedicated Agent 9 seven-viewport Chromium tests were green (runs 39350357013 and 39350356989).
- **Real screenshot QA:** Artifact `night-play-agent9-site-chrome` from run 39350356989 captures 320/360/390/412/640/760/1280. Confirmed game back/settings bar is visible at 320 following sticky-header fix, duplicate bottom Exit is absent, no horizontal overflow from dashboard header. Desktop 1280 screenshot retains approved NIGHT identity. Decorative board illustration contrast was softened on narrow phones.
- **Newest in-progress source revision:** `17b4c0e`. Adds **actual 2048 key/touch swipe**, settings opening/closing, library visual screenshot/overflow/cards assertion and profile screenshots at all seven widths. Browser and full-preview Actions runs 39350795340 / 39350795341 were queued at the time of writing; check their final state and do not claim they passed yet.
- **Own editable paths:** `night-play/site-shell.css`, `night-play/ui/` and `.github/workflows/night-play-agent9-ui.yml` only. Do not edit the coordinator-owned `night-play/index.html`, other agents' game modules, or frozen Channels.
- **Recovery action:** Retrieve the latest completed workflow artifact for run 39350795340; inspect `library-320.png`, `profile-320.png`, `site-320.png`, `game-2048-320.png`, `game-after-move-320.png` and 390/1280 equivalents. If CI fails, fetch its exact failing job log, fix and replay; if green, update the draft PR with evidence and request integration QA. Verify merged combined preview separately before `DONE`.
