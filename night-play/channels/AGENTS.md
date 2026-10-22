# NIGHT Channels — Agent Implementation Specification and 40-Minute Work Contract

**Locked product direction:** 22 October 2026 (user-approved conversation).  
**Coordination workboard:** https://github.com/Tomex777/Build/issues/568  
**Authoritative website branch as of writing:** `feature/party-online-rooms-20261019` — check latest integration head before creating PRs.  
**Canonical owner-facing website:** https://night-play-combined-preview.vercel.app/  
**Protected backend:** `Tomex777/Build/mscc/`, `mscc-azure`; **user's beta media/channel fork:** private `Tomex777/baileys` (currently identifies its package as `@itsliaaa/baileys`; confirm the exact commit and tested API before changes).  
**This is a work plan, not evidence that any agent has been launched, any feature is finished, or any production deployment occurred.**

## 0. READ FIRST / Non-negotiable constraints

1. Read `night-play/WBR.md`, `night-play/AGENTS.md`, `night-play/channels/DESIGN-LOCK.md`, `night-play/channels/browser-smoke.cjs`, and relevant code before editing. Root repository holds many unrelated apps; do not change them.
2. **Retain the approved Your Channels UI and navigation** (baseline `13331aa`, design lock). Add a carefully integrated **Channel configuration / content management** route within the existing site; do not unilaterally restyle the approved channel list, reconnect workflow, brand identity, or remove Anonymous NIGHT. No fake channel names, duplicated actions, generic placeholder illustrations, unnecessary filler, default ugly controls or emoji-as-icons. Use proper local SVG icons, quiet flat NIGHT style and accessible touch controls.
3. **One website:** test and ultimately integrate into `night-play-combined-preview`. A branch, mock or per-commit preview is diagnostic only. Verify alias -> deployed SHA + actual behavior. Do not claim a change is live if it remains on a PR.
4. **Security:** verified owner and channel admin control only; deny by default; every module independently disabled by default. Owner A cannot see, edit, enqueue, publish, or read history for B's channel. A website switch without a server-side authorization check is unfinished. Disconnect or loss of admin rights immediately disallows sending.
5. **No unapproved Azure/production changes.** Backend changes follow separate PRs into `mscc-azure` and protected release-batch workflow, with end-to-end test and explicit release approval. Preserve existing MSCC bot and other working services. Never migrate working features to spare tiny instances for no reason.
6. User explicitly wants a **bot-powered** channel automation manager, **not an expensive LLM** and not a renamed "AI Channels" page. Do not introduce automatic paid-model dependencies. Existing emergency **channel AI default-OFF** policy remains independent and enforced.
7. Own/reuse sources legitimately: check Reddit API licensing, rate limits and authorization; preserve attribution/source when appropriate; do not hotlink arbitrary third-party media without rights or policies. K-drama news must include verified sources, attribution and short *original* recaps rather than rehosting articles.
8. Do not silently change user-approved wording: **caption signature = person who posts; on-image watermark = exact verified channel display name**. They are NOT interchangeable.

## 1. Exactly 40 minutes per activated agent — ACTIVE execution

**When the user starts/resumes you for a NIGHT Channels role, run exactly a 40-minute wall-clock engineering session, no shorter/no longer, if the environment allows it.** This request is about each ACTUALLY RUNNING agent session, not about merely creating this Markdown. You cannot silently launch future/background agents by writing instructions.

- At start, note start time (UTC), claim **one** available task in issue #568, state Git branch, owned paths and dependencies. Inspect existing branches/PRs/claims first.
- Spend time **implementing/testing/fixing**, not watching idle builds or repeatedly writing status. Poll active builds until terminal pass/fail; work on another independent task while waiting where safe.
- Once your current section is implemented, **run interactive visual tests**, capture and actually inspect screenshots, reproduce/fix issues, rerun. A green build, syntax pass, one screenshot, or source-only review is **NOT** a visual test.
- **Only AFTER passing visual tests** may you switch to a different section **whose actual owner is not currently working on it** (unclaimed or explicitly handed off). Claim this next section in #568. Do not overwrite another agent's branch or shared files, and do not "help" by parallel editing their lock. If none is free, deepen your own area: edge cases, negative tests, mobile QA, failure recovery, performance and docs.
- Work actively through the requested 40-minute boundary, then commit working progress, post `HANDOFF` and stop. Log start/end timestamps, elapsed minutes, commit/PR, changed paths, test commands/results, screenshots and next precise action. Never falsely report exactly 40 minutes if interrupted; if tools/runner impose an earlier cutoff, note the actual duration and partial work. Never claim continued background work beyond the live session.
- A human/session launcher must start independent agent sessions. **Presence is real, not inferred from an assignment row.**

### Required check-in format (GitHub issue #568)
`CLAIM [A/B/C/D/E] | start <UTC> | branch <...> | files <...> | dependencies <...>`  
`STATUS | working change(s), tests, blockers, live PR`  
`VERIFIED | actual browser/device test, viewport, screenshots, failures fixed`  
`HANDOFF | end <UTC> | elapsed <duration> | PR/commit | merged? | main-preview SHA? | evidence | next action`

Avoid simultaneously editing the same file. Coordinator grants locks over the shared `channels/index.html`, `night-play/site-shell.css`, `night-play/vercel.json`, central MSCC router/storage, and shared CI. Agents add testable independent modules/adapters and propose interface changes instead of overwriting each other.

## 2. Product: per-channel configurable automation, not only anonymous posts

The owner connects/claims channels using the **existing verified WhatsApp consent/admin proof** flow. After selecting a verified channel, open **Configure channel**. Feature modules (all default OFF, including on newly linked or migrated channels):

- **Memes**: categories multi-select: relatable, dark (extra opt-in), wholesome, anime, Nigerian, animals, relationship, others. One choice for posting **mode**: `Review first` (manual) or `Automatic`. Schedule, quantity/cadence, optional caption filter and watermark apply **per channel**. Do not add redundant switches to represent the same choice.
- **News**: K-drama first; future anime/gaming niches. Select interests such as casting, release dates, trailers, renewals, romance genre, platforms. Owner chooses `Review first` or `Automatic`, schedule and spoiler policy. Source freshness is much stricter for news than for humour. Duplicate stories across outlets consolidate rather than spamming.
- **Anonymous NIGHT**: keep current owner-scoped submission/moderation workflow; its own switch and independent permissions.
- **AI responses**: optional independent OFF-by-default existing guard. Do not conflate it with memes/news; no AI requirement to ship automation.
- **Branding**: optional caption signature (poster) and image watermark (channel), not a module that publishes by itself.
- **Publishing/scheduling**: shows upcoming, sent, failed, cancelled with actual states; owner can cancel before send. Scheduling works for **manual-approved** content AND automatic content.

Switches persist server-side per authenticated owner + verified channel JID (and explicitly bound posting account). Disabling a module must cancel/suppress queued and in-flight publication *at the last send authorization check*, not merely hide its UI. Re-linking or account changes must not inherit approvals. Handle timezone and daylight saving correctly; store UTC for due times and owner's display timezone separately.

## 3. Meme discovery, funny ranking and four-card review

A `Find memes` control **retrieves**, it does NOT generate new meme images. Present **four visually complete candidates per batch**, each with loading/error states, source/attribution where required, category and an editable proposed caption. Owner can **Approve**, **Decline** or **Skip/next batch**; decisions are persisted (no repeated candidates in same session); approved items remain in a visible selection tray/count.

At the end, **Publish approved** is *one action* that queues the approved items as individual WhatsApp Channel posts, with safe pacing and per-item results (do not imply a single WhatsApp album unless verified). Alternative: **Schedule approved** chooses time and spacing. Review items not approved MUST never publish. Owners can edit the caption per candidate and preview its actual post format/watermark before enqueueing. If the user leaves/reloads, pending decisions should survive for a reasonable session.

**Scoring is not just newest/highest-upvoted:** use humour-interest match, prior human approvals/rejections, positive engagement signals, image legibility/quality, originality/near-duplicate hashes, diversity and low-weight freshness. A three-year-old genuinely funny meme **must remain eligible** unless already used/repetitive. Dark humour is opt-in and still subject to explicit safety/source rules. Avoid overclaiming "AI finds funny" when it is heuristics + human feedback. Develop with fixture feeds if API rights are pending, but never pretend fixture content is a live channel feed.

Suggested deterministic pipeline: permitted source adapter -> source metadata -> file validation/size caps -> optional cached preview -> category/quality score -> perceptual + URL/content duplicate check -> top diverse four -> review feedback -> publish queue. Include cache TTL, backoff, pagination, API quotas and failure handling. Preserve provenance/license metadata and report missing/expired source instead of posting corrupt images.

## 4. News ingestion / K-drama first

Use legitimately accessible sources (official studios/platforms, public news feeds with reuse permission) and link back. Classify verified information vs rumor, source-date, article-date, show title, genre, spoilers, location/availability. Auto mode must **never** assert casting rumours as fact. Deduplicate equivalent stories, choose original short summaries with date and link. Prefer rule/template-based summaries for structured release announcements; route nuanced/unverifiable claims to human review. No paid generative model required. Owner can follow tags or specific shows and control posting limits. More niches use the same source-adapter interface; do not hardcode K-drama throughout core queue.

## 5. Caption, author signature, word replacement (NO OCR in v1)

**Caption composition for any content with a human author:**
- Preserve/edit the main caption as applicable. Empty caption is allowed.
- Optionally apply owner-approved *text-only* lexical replacements with word/phrase boundaries, Unicode-aware normalization and case rules. Examples: `bullshit` -> `BS`; `shit` -> `shii`; `what the fuck` -> `WTF`; `fuck` -> `f*ck` (configurable, with semantic/double-transformation tests). Existing abbreviations like `WTF` or `BS` remain. Avoid false positives inside innocent longer words and avoid making harmful text more offensive. **These substitutions are a stylistic preference, NOT a guarantee that WhatsApp moderation will not act.**
- The optional signature beneath the caption is a WhatsApp-style quote of the **PERSON posting/submitting**, e.g. `> TOMEX`, not the channel name. For automatic content, owner must configure an explicit display persona or leave the signature OFF; do not invent a human author. Test actual WhatsApp Channel formatted captions on the selected fork; if blockquote syntax renders differently, implement/test a faithful approved fallback before shipping.
- No generic `— Night` signature unless the actual poster name is Night. Signature belongs to post text; watermark belongs to media pixels.
- If no caption, the signature can stand alone if enabled. Do not add filler just to fill space. Ensure output respects actual WhatsApp caption length and channel message type limits.

**Image-text censorship is OUT OF SCOPE for v1**: OCR would only be needed to *locate words already baked inside images*. Avoid shipping CPU-heavy OCR on the 1 GB bot server without measured budgets. In review mode owners can decline images with unwanted embedded text. Future optional manual precise region overlay/blur is possible; Sharp can *cover/composite* known coordinates but cannot locate text by itself. Never advertise full image moderation or censorship unless implemented/tested.

## 6. Exact-channel-name reusable semi-transparent watermark

**Different fields:** the poster signature uses the person. Watermark uses the **exact verified WhatsApp channel display name**, including Unicode symbols, stylized combining marks and emoji (preserve codepoints/grapheme clusters, whitespace and logical order). Do not silently replace the name with ASCII, omit emoji or inject a different brand. Warn when glyph coverage fails and offer an owner-uploaded transparent PNG/approved asset as an explicit fallback.

- Create a reusable transparent-background watermark PNG/WebP asset on linking, renaming, or branding-config revision, cache using (channel-id + exact display-name + style version); permit reuse/export. Do **not** re-render the text for every post.
- **Both text and any color emojis are translucent**: apply uniform alpha to the final rendered watermark layer; target roughly 15–30% adjustable after visual review, not opaque pixels on transparent canvas. Source image must remain legible **through** the glyphs, while glyphs remain perceptible.
- Auto choose black or white/light text using sampled underlying region contrast, with edge padding and safe positioning; user may choose position/opacity. Never block meme punchline/text or crop essential content. For light/dark switching, cache light and dark layers as variants; composite via **Sharp** at send/preview time.
- Accurate Unicode text shaping and emoji fallback may require a font-aware renderer (e.g., Pango/Cairo or controlled Chromium SVG/HTML render to alpha PNG) followed by Sharp; **Sharp alone does not guarantee emoji/color-font support**. Unicode codepoints can match the WhatsApp name even if glyph appearance differs across fonts. Audit mixed scripts, emoji ZWJ sequences, skin tones, variation selectors, RTL and long channel names. Do not claim the watermark looks **exactly like WhatsApp's glyphs** without visual proof. User-supplied transparent overlay remains the fidelity escape hatch.
- Bound all asset dimensions, memory, file size and cache; reject unsafe SVG/URLs, sanitize file names, prohibit HTML injection. Treat animated media/videos separately rather than flattening or silently losing their animation; watermark on static JPEG/PNG/WebP is the first release gate.
- Never reuse Channel A's watermark for Channel B or retain stale branding after channel unlink/revoke.

## 7. Backend & publishing contracts (must be real)

Fit existing `mscc/` RPC/signature/auth/SQLite patterns; do not invent direct unauthenticated HTTP posting endpoints. Suggested records (names are illustrative; first inspect schema to avoid duplicating existing components):

- `channel_settings`: owner, channel JID, verified bot account, per-feature opt-in/mode/interests, signature, profanity preference, watermark opts/version, timezone, optimistic revision.
- `content_candidates`: source id/URL, media hash, source provenance/license, category, scores, accepted/declined/skipped, proposed caption, expiry.
- `channel_posts`: channel ID, module, author/persona, media asset, immutable approved caption snapshot, watermark version, `draft|approved|queued|sending|sent|failed|cancelled`, due UTC, idempotency key, attempt metadata and WhatsApp message id when available.
- `review_batches`: owner and channel scoped, four candidates, selection history and expiration.

**Always validate** login, verified channel/admin ownership, linked account/JID, module enabled, approved state and current permissions **before queuing AND immediately before sending**, including after retries/restarts and mid-flight toggles. Scheduled jobs must survive restart, use durable leases/idempotency, avoid double send and respect serialized/rate-limited media upload. Fail closed on permission/storage errors. Show truthful delivery outcomes, do not mark sent on an HTTP 200 that only enqueues. No cross-account personality posting.

**User's fork:** use `Tomex777/baileys` for controlled beta channel-media publishing compatibility; inspect existing MSCC dependency lock and fork commit before updating and test on a sandbox/approved test channel. The fork README/package currently advertises newsletter media upload fixes; that is a claim to validate by tests, **not a verified production fix**. No unauthorized production rollout; no fork credentials in PRs. Protect the separate emergency channel-AI policy `mscc/channel-ai-policy.js`: content-module opt-ins are NOT permission to react/AI reply in channels.

## 8. 1 GB server / 256 MB auxiliary servers

Start with lightweight server-side caption regex replacement, cache, schedule and **Sharp** static overlay, *not OCR*. Set a **single image compositor worker at first**, cap input pixels (decode bombs), output bytes, file count, work queue depth, per-job timeout and RSS; benchmark under actual MSCC multi-account memory/CPU usage before claiming capacity. Use cached watermarks and keep unnecessary binary buffers out of long-lived process memory. Display errors and retries without exhausting the WhatsApp connection.

Four small ~256 MB auxiliary instances are **optional expansion**, not a mandatory migration or a dependency for critical active Azure bot features. OCR/advanced processing, if ever needed, goes to an isolated optional background worker only after real memory and reliability measurements (and never if it breaks on small RAM). Queued approved posts should not be lost if an optional helper is down.

## 9. Agent ownership (claim in #568; roles may be distributed across actual sessions)

| Lane | Primary owner / independently testable work | Coordination / merge lock |
| --- | --- | --- |
| **A — Channels UI** | `night-play/channels/` new config flow, switches, four-card review, caption editor, selections, queue/schedule states; real mobile/desktop screenshots; preserve locked "Your channels" visual | Coordinator owns global shell, `index.html`, `site-shell.css`, preview integration. |
| **B — Curation & News** | Isolated meme source adapter, ranking, dedupe, interests, legal metadata, news/K-drama adapters; deterministic tests + real-source handling | Coordinate candidate API shapes with A and queues with C. |
| **C — Publisher & Security** | `mscc/` durable owner-scoped settings, queued/scheduled media posts, auth and final send guard; use/test user's Baileys fork on approved beta | Exclusive MSCC router/storage/dependency lock and protected rollout. |
| **D — Captions & Branding** | Standalone text sanitizer, signed human quote formatting, exact Unicode/emoji transparent watermark assets using Sharp/renderer, cache/export, low-RAM tests | API contract with C; candidate editor preview with A; do not edit shared router without C's lock. |
| **E — Independent QA/Integration** | WBR visual acceptance, browser 320/360/390/412/desktop, ownership abuse tests, real formatting/media posting on authorized test channel, resource/error tests, PR/preview SHA truth | QA verifies independently; coordinator alone integrates shared website and coordinates backend release. |

**Agent taking over idle work:** first post `CLAIM` with the previously unclaimed lane and existing handoff state, then work in its isolated files. Do not infer that another lane is idle merely because its agent is not replying to you; inspect active claims and PRs, request the coordinator to confirm a lock if ambiguous.

### Integration / staged gates

1. **Contracts and unit tests:** Agree settings, candidate, post schemas and security behavior; text-only and image transforms testable offline.
2. **UI + real data path:** Dashboard talks to signed backend, no fake content shown as live. Visual capture and fixed defects for 320/360/390/412 and desktop, including keyboard/focus and long emoji names.
3. **Authorized channel publishing smoke:** On a test channel verify text+blockquote signature, JPEG/PNG watermark, image caption, exact account/channel, manual approval/no approval, scheduled/manual/auto job, toggle revocation before send, retries, idempotency, disconnect and failure states.
4. **No regression:** Run `node night-play/channels/browser-smoke.cjs`, `node night-play/selftest.mjs`, affected NIGHT Play CI and selective MSCC self-tests/full `npm run check` where feasible; inspect other website routes and preserve games. Missing tooling is a disclosed block, not a pass.
5. **Release truth:** Merge reviewable site PR to the **current shared integration line**, update/test same main combined-preview project, inspect its real deployment SHA and interactive behavior; backend PR remains protected until authorized Azure batch/smoke. If only some gates pass, state **implemented but not yet live**.

## 10. Explicit acceptance checks / non-goals

**Acceptance checks:** owner A cannot target owner B's channel; feature OFF by default; no content after OFF/disconnect; four useful preview cards and persisted review choices; no declined post; one-click queued sequential approved posts and manual schedule; old funny memes can score well; targeted interests; K-drama credible provenance; `> Poster Name` under caption, not channel name; text replacements don't overmatch; exact Unicode/emoji channel name watermark semi-transparent across the whole layer; text remains visible under it; reusable asset; no unnecessary OCR; responsive approved NIGHT UI; real pictures, not placeholders; explicit send result and duplicate protection.

**Not promised in v1:** perfect machine judgment of humour, full automated fact-checking, arbitrary text recognition inside meme images, magical evasion of WhatsApp content policies, automatic emoji appearance identical to the WhatsApp app across devices, scalable high-volume video editing, autonomous background agents or unapproved production releases.

**If a scenario is unverified, record it and continue fixing/testing, not declaring DONE.**
