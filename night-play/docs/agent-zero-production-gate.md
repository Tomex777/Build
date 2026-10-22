# Agent Zero — NIGHT Play production release gate

**User-approved objective:** finish the project, actually play all games and publish a polished version to the combined preview, followed by real production. Status must be proved, not inferred from PR merge or Vercel READY.

## Production checklist — only PASS or transparent BLOCKED/FAIL

| Gate | Release evidence |
| --- | --- |
| Branch integrity | Canonical integration SHA, complete diff/PR ledger, all required fixes actually merged |
| 31+ live tiles | Generate inventory from `night-play/qa/agent10-catalog-gate.mjs`; for **each**, name route + full-round touch/keyboard game tested + score/lose/win/restart or a documented reason NOT_TESTED |
| 5 viewports | 320, 360, 390, 412, 1280 CSS px, no viewport overflow or tiny unreachable touch targets |
| Gesture contract | First swipe always works; pointerdown/move/up/cancel/capture/blur correct; long-press/drag physical motion not misinterpreted as taps; ≥30 consecutive actual CDP touch inputs and rapid sequences |
| Piano Tiles | Actually tap **visible** notes outside previous ±235ms invisible gate, 40+ endless notes, freeze long tile under finger, full Frère Jacques and Twinkle win with chords, early/late misses, clear restart |
| Sky Jumper | Tap and hold anywhere → **no player motion**, drag relative to original player position, no jump/teleport on finger position, 320/390/412 touch checked |
| Snake | First gesture, 4 queued legal directions, reversal rejection, death and restart |
| Jigsaw 24/48/144 | Long drag with physical finger offsets, selection precision ≥97% even on randomized 144-piece scatter, piece accessibility after focus/magnification, real progress save/resume and ≥15 placements |
| WHOT | Actual full CPU match, Nigerian rules, smooth animation and correct winner/hand count; no overflowing CPU hand |
| All other games | Real full wins/losses/restarts; fair collisions/difficulty progression, no generic art or unusable controls |
| Preview delivery | Immutable READY preview URL, exact SHA match, deployed browser screenshots/touch/console; alias clearly audited |
| Account/multiplayer | Real WhatsApp identity + website name update propagation; ≥2 remote signed users with actual server rooms; otherwise mark BLOCKED and do not make false production claims |
| Android/low-end | Physical phone testing when available; browser Android emulation is **not** labeled physical device QA. 60fps target without uncontrolled redraw and slow gesture response |
| Security/privacy | No credentials in logs, no mutation of production MSCC bot without authorization, existing channel/account APIs unharmed |
| Release | Stage production build, repeat critical tests there, promote only after all P0/P1 PASS and owner authorization (already requested in chat), inspect live logs and verify `night-play.vercel.app`, keep rollback |

**Known NO-GO at runbook creation:** Independent QA reported 144-piece Jigsaw overlap/wrong selection, mobile 320px some randomized layouts <97% accurate. Authenticated production multiplayer remains unproven. The previous canonical combined preview is SHA `90886378`, missing later #486 Piano touch/#487 Sky relative gesture fixes. Those were merged to `b9aeb682` on October 20 but deployment/test proof must be rechecked.

### Recommended executable test sequence

```sh
# from repo root, use existing reproducible tests; ensure Playwright Chrome is actually installed
node night-play/selftest.mjs
node night-play/qa/gameplay-gate.mjs
node night-play/action/agent0-sky-touch.mjs
node night-play/action/agent3-wave2-touch.mjs
node night-play/piano-tiles/mobile-smoke.mjs
node night-play/qa/agent10-catalog-gate.mjs
node night-play/jigsaw/difficulty-audit.mjs
node night-play/jigsaw/visual-play-audit.mjs
node night-play/jigsaw/mobile-smoke.mjs
node night-play/club/mobile-smoke.mjs
node night-play/arcade/mobile-smoke.mjs
node night-play/qa/agent10-arcade-gameplay.mjs
node night-play/ui/site-chrome-smoke.mjs
# AFTER preview deploy, verify deployed SHA, then:
NIGHT_PLAY_BASE_URL=https://<SHA-specific-preview>.vercel.app node night-play/qa/agent10-deployed-gameplay.mjs
```

Some scripts only cover smoke/happy path and MUST be augmented with hands-on play for whole games. Each script's exit code, runtime and artifacts must be recorded. **Do not loosen tests to fake release green.**

### Decision template

```text
[AGENT ZERO][RELEASE GATE]
Integration SHA:
Preview URL and deployed SHA:
Inventory total / real full-round PASS / FAIL / NOT_RUN:
P0 touch:
P1 144-piece Jigsaw and other quality:
Real MSCC authentication & remote clients:
Physical Android:
Visual inspection screenshots:
Preprod proof:
GO / NO-GO and exact blockers:
Production URL/verification (ONLY if actually promoted):
Next concrete action:
```
