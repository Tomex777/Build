# NIGHT Channels — Agent E independent QA and release gate

**Status checked:** 2026-10-22, 20:00 UTC (E resumed review). **Authoritative site:** https://night-play-combined-preview.vercel.app/channels/ . **Review thread:** https://github.com/Tomex777/Build/issues/568 . **QA PR:** https://github.com/Tomex777/Build/pull/570 .

This is an engineering acceptance record, **not** proof that a test run has finished, a screenshot has been visually inspected, a website has been deployed, or a WhatsApp channel has received media. Do not turn a queued Actions job into a PASS.

## Exact execution

Use a Node 22 checkout of `Tomex777/Build` on `agentE/channels-qa-20261022`, with `playwright@1.56.1` available and Chrome installed:

```sh
node night-play/channels/qa/independent-browser.cjs
node night-play/channels/qa/exact-channel-name.mjs
NIGHT_SOURCE_ROOT=agentA/night-play node night-play/channels/qa/config-browser.cjs
NIGHT_CURATION_ROOT=agentB/night-play/channels/curation node night-play/channels/qa/curation-contract.cjs
NIGHT_BRANDING_ROOT=agentD/mscc/channel-branding node night-play/channels/qa/branding-contract.mjs
```

For the cross-agent lines, check out the actual respective **branches** under `agentA/`, `agentB/`, `agentD/` at the repository root first. They are not part of E's source branch. E's workflow `.github/workflows/night-channels-agent-e-qa.yml` automates the checkouts and the Chrome screenshots.

## Acceptance matrix — default blocked until real proof

| Release risk | Actual check | Evidence required | Present evidence |
| --- | --- | --- | --- |
| Wrong owner or channel | Separate A/B browser contexts, stale revoked owner, foreign JID POST | Real signed account + MSCC request logs deny cross-owner access and after-claim revocation | Deterministic fake service only; live NOT_RUN |
| Hidden browser overflow / touch | 320, 360, 390, 412 px and 1280 px, screenshot per breakpoint | Inspect original screenshots; retest first tap, rotation and scroll on device | Screenshot code committed; GitHub runner QUEUED — no artifact inspected |
| Module opt-in | All four switches OFF until server confirms; explicit POST, revision lock | Bot settings persist, reload restores, OFF cancels pending queued sends | UI mock tests only; MSCC C config API NOT_AVAILABLE |
| Four-card meme selection | Four real candidates, prior approvals kept after Next four, individual approvals, edit/skip, fixed rights flags | Real rights-verified source images; A+C persisted tray, no silent approvals | A source retains only current batch; E's reload/Skip gates added, cross-batch persistence still P1 |
| Rights safe and provenance | `publishable:false` review-only preview, not approvable; immutable media rights evidence | C denies unlicensed media at approve, enqueue **and** final send | B source now propagates rights; A UI requires publishable/verified; C server gate NOT_RUN |
| News | Verified sources, K-drama interests, rumor not auto, spoilers default withheld, stale-story dedupe | Actual signed feed time/provenance check and posting outcome | B core source smoke; live NOT_RUN |
| Poster vs channel identity | Exact WhatsApp channel name watermark; `> Person` explicit caption signer; Unicode ZWJ retained | Same real channel metadata at ownership and send; color emoji pixel inspection | D matches A's canonical `branding.posterName`; baseline MSCC strips U+200D, P1 |
| Watermark performance | Static media <=8 MiB, D's revised <=4 MP source cap, bounded cache, bytes/CPU | PNG transparency, correct original emoji, mobile bandwidth and <=1GB RAM server measurement | D source guard checked; live metrics NOT_RUN |
| Job queue / delivery | Schedule, retry, idempotency, multi-poster room, auto OFF, cancel on unlink / lost admin | Real authorized test channel, two distinct bot accounts, message IDs, sent-media proof | C publisher integration not present; NOT_RUN |
| Deployment truth | Alias's real deployment ID and Git commit SHA, live route and console | Exactly integrated SHA served on canonical combined preview | 20:00 UTC alias points to READY `e27d07b2b5c3` (`dpl_5okDQCGBmeAmq7niX9jCZbWGL8sW`) but lacks agent Channels config |

## Defects that block release

1. **Real verified name corruption:** `mscc/night-play-channels.js` sanitizer strips Unicode format characters including U+200D, and a raw 100-code-unit slice can truncate a surrogate pair. Repair backend authoritative name capture and retain exact codepoints when rendering watermark.
2. **Approval loss across four-card batches and missing Skip:** A's current `findMemes(next)` clears decisions and `approved()` only considers the current batch; candidate actions offer Approve / Decline / Preview post, but no distinct Skip. Require a durable cross-batch tray with server-side selection snapshot plus actual per-candidate Skip, including reload. E added independent same-batch reload and missing-Skip checks.
3. **License and source gate:** B now carries `publishable`, `rightsStatus`, `reviewOnly` and blocked reasons; A source disables Approve unless `publishable===true` and `rightsStatus==='verified'` and not review-only. Independent browser proof remains QUEUED; C must enforce *server-held* licensing at approval, enqueue and final send, never trust browser fields.
4. **Branding field alignment in source only:** A and D now agree on `branding.posterName` for automatic persona; D uses the authenticated real poster for manual content. C still must persist exact field and demonstrate save→reload→caption, immutable approved-post snapshots, and preview/send parity.
5. **Back-end integration unavailable:** No assurance per-channel state, schedule, queue, dedupe, admin recheck, or publish is deployed. Keep default OFF.

## Evidence integrity and completion

Record each run with branch and SHA, runner URL, artifact checksum and screenshot thumbnails. A green compile is not browser QA, a passed local mock is not real authorization, and a newly uploaded source file is not a deployed website. After acceptance on integrated source, inspect **the canonical alias**, not a specialist branch, and compare deployed SHA before any release claim. Do not use unauthorized WhatsApp channels or test posts. For release, keep production MSCC unchanged until approved and independent verified media send succeeds.

**As of the 20:00 UTC check:** workflow #39834018188 has five QUEUED jobs; visual artifacts are not available for inspection. A/B/D remain separate PRs and C's signed queue is not integrated. Status: **NOT VERIFIED / NO RELEASE**; changes in this document or QA test do not deploy the combined preview.
