# NIGHT Channels — server-owned multi-batch approval contract (Agent A ↔ C)

**Status:** reviewable UI PR #572 only, not a deployed/publishing feature. Owner/channel/account authorization and final send remain C's protected MSCC responsibility. Canonical user-facing destination is the **existing** NIGHT Play combined preview. Do not create a feature-specific deployment.

## Snapshot returned by channel:memes:find/current/review/publish

```json
{"selection":{"id":"selection_abcd1234","revision":3,"items":[{"batchId":"batch_abcd1234","candidateId":"meme_abc12","title":"Approved meme","caption":"Owner-confirmed text","rightsStatus":"verified","publishable":true,"reviewOnly":false,"previewUrl":"/api/channel-media/authorized-asset-token"}]}}
```

The initial empty `items:[]` snapshot is a real durable server record, never an optimistic browser default. Maximum 10 approved items (Agent C's bounded server limit); previews may use bounded PNG/JPEG/WebP `previewData` or owner-authorized same-origin `/api/channel-media/...` URLs, never third-party hotlinks. The server must preserve original candidate/media hash, rights license provenance, owner, channel JID and publishing WhatsApp account *independently of client fields*.

1. `channel:memes:current` returns `{selection}` even with **no active batch**; when present also include current `batchId`, exactly four `candidates` and `decisions` (selected state must survive reload).
2. `channel:memes:find` returns four rights-labeled sourced candidate previews **plus the same selection id/approved items**; `next:true` must NOT discard any approved selection. Avoid redisplaying already reviewed content when possible.
3. `channel:memes:review` receives `{channelId,batchId,candidateId,decision:'approved'|'declined'|'skipped',caption,expectedSelectionRevision}`. Reject stale revision with HTTP 409. Verify ownership/linked bot account/admin AND candidate license before approval. Apply decision durably, increment revision, return `{candidateId,decision,caption,selection}`. A skipped or declined item from *any previously reviewed batch* is removed, even after Next four.
4. `channel:memes:publish` receives `{channelId,selectionId,expectedSelectionRevision,mode:'now'|'schedule',scheduledAt?,timezone?,spacingMinutes?}`, **not one batchId**. Atomically check owner, linked bot/admin rights, memes module ON, every candidate's immutable rights and approval snapshot, timezone/schedule and idempotency. Queue exactly **one job per item** and atomically clear the server selection. Return `{jobs:[...],selection:{id,revision,items:[]}}`; replay cannot double-send. A missing or malformed snapshot blocks the UI rather than reverting to the old single-batch publish.
5. Immediately before each actual WhatsApp send and after retries/restarts, repeat account/channel/admin, module ON, license, approval and cancellation guards. Revocation or module OFF must prevent delivery. Report queued/scheduled/sent/failed/cancelled truthfully; HTTP success from an enqueue is NOT a confirmed WhatsApp send.
6. Keep `branding.posterName` as canonical poster **person** for automated caption signature (agreed with D); watermark text comes only from the exact verified Unicode/emoji **channel name**. Final-media preview and send share D's formatter.

## Acceptance / release gates

Approve A1, edit caption, decline A2, reload => A1 survives. Next four => A1 still visible. Approve B1 => 2 items; reload => both. Remove B1 from tray via Skip => only A1 remains. Publish once => one A1 job, never declined or rights-unknown media; no duplicate from retry. Schedule and cancel are separately verified. Unauthorized owner/channel selection id, stale revision, OFF/revocation, missing server and candidate-rights mismatch all fail closed. Chrome 320/360/390/412/1280 interactions/screenshots must be inspected, not merely source checked. A's mock Chrome test is NOT a real authenticated MSCC/WhatsApp publishing test. E independently checks, coordinator integrates to the *current* shared branch then updates the existing canonical combined-preview alias.

## Current A↔C integration differences (NO RELEASE)

- Agent A enables the exact-channel-name watermark only when the signed, owner-scoped channel response includes `verifiedDisplayName` and `verifiedNameSource:'whatsapp-raw'`. The initial C draft `channel:list` returns `name` but not these proof fields; add an authoritative server-derived field (not browser input). Until corrected, watermark activation intentionally stays disabled.
- Agent A offers D's `watermarkPosition:'auto'` and `watermarkOpacity:0.22`; initial C patchConfig rejects both. C must accept these and preserve bounded compositor validation; do not silently remove auto quiet-corner behavior in A.
- Current A caption replacements conservatively use C's 20 rules of 40-character phrases and 60-character replacements; D's formatter supports a larger bound. The service/validator should converge before release.
- Agent A now gates source discovery and all review decisions when the Memes module is OFF (C `review()` denies OFF), and locks all asynchronous review buttons during module saves. Exact-head interactive CI remains a release gate.
