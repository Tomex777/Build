# NIGHT Channels — Agent E independent QA and release gate

**Status checked:** 2026-10-22, 20:30 UTC (E independent A/B/C/D source and isolated Node checks). **Authoritative site:** https://night-play-combined-preview.vercel.app/channels/ . **Review thread:** https://github.com/Tomex777/Build/issues/568 . **QA PR:** https://github.com/Tomex777/Build/pull/570 .

This is an engineering acceptance record, **not** proof that a test run has finished, a screenshot has been visually inspected, a website has been deployed, or a WhatsApp channel has received media. Do not turn a queued Actions job into a PASS.

## Exact execution

Use a Node 22 checkout of `Tomex777/Build` on `agentE/channels-qa-20261022`, with `playwright@1.56.1` available and Chrome installed:

```sh
node night-play/channels/qa/independent-browser.cjs
node night-play/channels/qa/exact-channel-name.mjs
NIGHT_SOURCE_ROOT=agentA/night-play node night-play/channels/qa/config-browser.cjs
NIGHT_CURATION_ROOT=agentB/night-play/channels/curation node night-play/channels/qa/curation-contract.cjs
NIGHT_BRANDING_ROOT=agentD/mscc/channel-branding node night-play/channels/qa/branding-contract.mjs
NIGHT_A_SOURCE_ROOT=agentA/night-play NIGHT_C_SOURCE_ROOT=agentC/mscc node night-play/channels/qa/a-c-config-contract.cjs
NIGHT_MSCC_SOURCE_ROOT=agentC/mscc node night-play/channels/qa/exact-channel-name.mjs
node agentC/mscc/channel-publisher/selftest.js
```

For the cross-agent lines, check out the actual respective **branches** under `agentA/`, `agentB/`, `agentC/`, `agentD/` at the repository root first. They are not part of E's source branch. E's workflow `.github/workflows/night-channels-agent-e-qa.yml` automates the checkouts and the Chrome screenshots.

## Acceptance matrix — default blocked until real proof

| Release risk | Actual check | Evidence required | Present evidence |
| --- | --- | --- | --- |
| Wrong owner or channel | Separate A/B browser contexts, stale revoked owner, foreign JID POST | Real signed account + MSCC request logs deny cross-owner access and after-claim revocation | Deterministic fake service only; live NOT_RUN |
| Hidden browser overflow / touch | 320, 360, 390, 412 px and 1280 px, screenshot per breakpoint | Inspect original screenshots; retest first tap, rotation and scroll on device | Screenshot code committed; GitHub runner QUEUED — no artifact inspected |
| Module opt-in | All four switches OFF until server confirms; explicit POST, revision lock | Bot settings persist, reload restores, OFF cancels pending queued sends | UI mock tests only; MSCC C config API NOT_AVAILABLE |
| Four-card meme selection | Four real candidates, prior approvals kept after Next four, individual approvals, edit/skip, fixed rights flags | Real rights-verified source images; A+C persisted tray, no silent approvals | A now implements signed selection ID/revision, cross-batch tray and explicit Skip in source; E updated fixture, real interactive Chrome still NOT_RUN |
| Rights safe and provenance | `publishable:false` review-only preview, not approvable; immutable media rights evidence | C denies unlicensed media at approve, enqueue **and** final send | B source now propagates rights; A UI requires publishable/verified; C server gate NOT_RUN |
| News | Verified sources, K-drama interests, rumor not auto, spoilers default withheld, stale-story dedupe | Actual signed feed time/provenance check and posting outcome | E tested B exact branch: curation contract PASS and B suite 43/43 PASS (isolated Node 24); live NOT_RUN |
| Poster vs channel identity | Exact WhatsApp channel name watermark; `> Person` explicit caption signer; Unicode ZWJ retained | Same real channel metadata at ownership and send; color emoji pixel inspection | A/D agree on `branding.posterName`; E exact-name test FAILS against production baseline but PASSES against C draft #597; Arabic glyph font-coverage gate P1 |
| Watermark performance | Static media <=8 MiB, D's revised <=4 MP source cap, bounded cache, bytes/CPU | PNG transparency, correct original emoji, mobile bandwidth and <=1GB RAM server measurement | D's actual caption, preview/send, watermark and integration suites 4/4 PASS with fonts/Sharp on isolated Node 24; native JPEG visuals inspected, found tofu glyphs; Azure metrics NOT_RUN |
| Job queue / delivery | Schedule, retry, idempotency, multi-poster room, auto OFF, cancel on unlink / lost admin | Real authorized test channel, two distinct bot accounts, message IDs, sent-media proof | C draft PR #597 now implements durable queue, rights/revocation logic; C SQLite publisher selftest, channels selftest, NIGHT Play selftest PASS in E isolated Node 24; no merge or real send |
| Deployment truth | Alias's real deployment ID and Git commit SHA, live route and console | Exactly integrated SHA served on canonical combined preview | 20:00 UTC alias points to READY `e27d07b2b5c3` (`dpl_5okDQCGBmeAmq7niX9jCZbWGL8sW`) but lacks agent Channels config |

## Defects that block release

1. **Real verified name corruption in protected baseline; fixed only in C draft:** On `mscc-azure`, `mscc/night-play-channels.js` sanitizer strips Unicode U+200D; E's exact verified name script actually fails. New C draft PR #597 passes the same E independent test on isolated Node 24. The fix must undergo protected merge and exact combined deployment before the live channel-name claim is valid.
2. **Selection durability gate (A source fix ready, not released):** In current A source as of 20:22 UTC, `selection.id` and `selection.revision` are server-authoritative, the saved tray persists across `Next four`, and every candidate has separate Approve/Decline/Skip. E's fixture now checks two-batch approvals, stale optimistic-concurrency 409, old-batch removal, reload and revoked-owner 403. Must still pass a real Chrome run against A source and real signed C API before release; mock-only evidence does not prove server durability.
3. **License and source gate:** B now carries `publishable`, `rightsStatus`, `reviewOnly` and blocked reasons; A source disables Approve unless `publishable===true` and `rightsStatus==='verified'` and not review-only. Independent browser proof remains QUEUED; C must enforce *server-held* licensing at approval, enqueue and final send, never trust browser fields.
4. **A/C branding schema mismatch (E reproduced real function):** A allows `watermarkPosition:'auto'` and `watermarkOpacity:0.22`; C actual `patchConfig` rejects both with 400. E's new `a-c-config-contract.cjs` evaluates C's actual validator and currently FAILS on precisely those two visible owner options. Reconcile C without dropping automatic quiet-corner functionality. `branding.posterName` is now aligned across A/C/D in source; still requires real save→reload→caption on live integrated backend.
5. **Native font glyph coverage P1:** E executed D's real `@napi-rs/canvas`/Sharp font-visual probe in an isolated Vercel sandbox with Noto Color Emoji. Probe returned `LIKELY_COLOR` with **only emoji drawn**, while the verified display name's Latin text was entirely absent. Explicitly registering Noto Sans restored Latin/Yoruba, but Arabic still showed tofu squares, *again despite green color-pixel status*. Added Noto Sans Arabic for a follow-up run but sandbox expired before viewing the newest output. Strengthen the probe to require readable RTL/Latin/ZWJ glyphs on approved Azure fonts, not merely 15 colored pixels; E inspected native synthetic dark/light cropped render images, not an authorized channel send.

6. **Back-end integration built in draft, not live:** C draft PR #597 now provides source-level SQLite per-channel configs, rights/approvals/queue, admin rechecks and no-send gates. E independently ran C selftests and exact-name check PASS on isolated Node 24. C still lacks B's trusted licensed source ingestion to provide real four-card images, and neither the protected worker nor A/B/C/D integrated website has a verified real WhatsApp send. Keep defaults OFF.

## Evidence integrity and completion

Record each run with branch and SHA, runner URL, artifact checksum and screenshot thumbnails. A green compile is not browser QA, a passed local mock is not real authorization, and a newly uploaded source file is not a deployed website. After acceptance on integrated source, inspect **the canonical alias**, not a specialist branch, and compare deployed SHA before any release claim. Do not use unauthorized WhatsApp channels or test posts. For release, keep production MSCC unchanged until approved and independent verified media send succeeds.

**At E's independent review (20:30 UTC):** Actual source tests: B `43/43` PASS, E B contract PASS, E D contract PASS, D 4/4 native test files PASS with configured fonts; C SQLite publisher, Channels and NIGHT Play tests PASS; exact Unicode metadata on C draft PASS, on protected baseline FAIL; **real A/C field parity FAIL** on `auto` and `0.22`. D's color-emoji pixel heuristic falsely passed unreadable Latin/Arabic in two inspected native mock composites. Browser route/320–1280 screen interaction **NOT_RUN** (sandbox Chromium GUI dependencies unavailable and GitHub Actions queued). A/B/C/D remain separate branches; canonical alias lacks unmerged config. **NO RELEASE / NO WHATSAPP POSTS**. This document does not itself deploy anything.
