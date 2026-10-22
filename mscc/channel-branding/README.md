# NIGHT Channels — Agent D: captions and branding

This directory is a **standalone, server-side component**, deliberately not wired into the live MSCC router or NIGHT Play UI. See `night-play/channels/AGENTS.md` and coordination [#568](https://github.com/Tomex777/Build/issues/568). It is **not live** and must not be treated as an authorization layer.

## Contracts to Agent A / Agent C

Each verified channel may opt in to branding independently. Suggested server-side revisioned fields (all OFF by default):

```json
{
  "branding": {
    "captionReplacementsEnabled": false,
    "captionReplacements": [{"from": "what the fuck", "to": "WTF"}],
    "signatureEnabled": false,
    "posterName": "",
    "watermarkEnabled": false,
    "watermarkOpacity": 0.22,
    "watermarkPosition": "auto",
    "watermarkStyleVersion": 1
  }
}
```

A must never describe local-only options as saved. C must validate values against verified, owner+channel+bot-account-scoped durable storage before enqueue AND final send. An image processing success is not proof a WhatsApp Channel post was delivered.

**Canonical preview/send transformation:** `prepareChannelContent({text,mediaBuffer,channelId,verifiedDisplayName,posterName,sourceKind:'manual'|'automation',branding,maxUtf8Bytes,rendererVerifiedEmoji,watermarkCache})` in `prepare.mjs`. It defaults every feature OFF, returns the original bytes unchanged when watermark is OFF, and rejects malformed enabled transformations. Use **the same helper** for A's authenticated server-rendered preview and C's final send to avoid what-you-saw/what-was-posted differences. It does *not* verify access or send: those are C's independently enforced gates.

**Authorship boundary:** `sourceKind:'manual'` accepts a signature only from the actual authenticated poster provided by C in `posterName` (never a configurable auto persona); `sourceKind:'automation'` may sign only with the owner-configured canonical `branding.posterName`, and fails closed when missing. Never derive the manual poster from a client-supplied name or from the channel's display name. Browser preview and final send must use the same author source. For reviewed and scheduled content, C must freeze the approved caption, author and watermark revision into an immutable approval snapshot: a later account/profile rename must not silently alter the reviewed post, and module OFF/revoked admin must still prevent final sending. Agent A currently stores `branding.posterName`, so that is the **canonical saved/transport field**. The D bridge temporarily accepts an older `signaturePosterName` only if no conflicting `posterName` exists; conflicting nonempty values fail closed until C migrates. C should persist one field (`posterName`), not two.

Caption: `composeChannelCaption({text,replacements:{enabled,rules},poster:{enabled,name},maxUtf8Bytes})`. The poster is the actual person/pseudonym explicitly specified by the poster (or an explicitly configured automatic-content persona); it is **not** the channel display name. Output signature uses a WhatsApp-text `> PERSON` line. WhatsApp's actual rendering must be tested on the **approved beta fork/test channel** before production, and a fallback must be verified if blockquotes differ.

Watermark: `makeReusableWatermark({channelId,displayName,tone,opacity,styleVersion,cache,...})` returns PNG bytes with the same verified channel name (without NFC/ASCII/emoji/ZWJ transformations); `watermarkStaticImage({input,channelId,displayName,cache,opacity,position,...})` composes JPEG/PNG/WebP. `WatermarkCache.invalidateChannel(jid)` on rename, unlink, revocation or transfer. Do not trust a client-supplied channel name: take it from the server's original verified WhatsApp metadata, and ensure the upstream `mscc/night-play-channels.js` sanitiser is corrected — its reported `\p{Cf}` removal currently breaks U+200D emoji and surrogate-pair truncation. **Do not silently watermark with a corrupted name.**

## Security / memory / visual notes

- Exported media transformations **do not post** or schedule anything. Agent C owns SQLite config/queue/permissions, final-send recheck and fork-specific media upload.
- Text replacements are opt-in, one-pass, phrase/Unicode word-boundary aware; do not advertise them as evading WhatsApp moderation. No OCR or in-image language recognition.
- Render name with existing `@napi-rs/canvas` Skia engine. On the deployment host register licensed, appropriate Unicode + color emoji fonts using `GlobalFonts.registerFromPath(...)` as required; no font files should be attached to users. Sharp's SVG text path was **visually observed** producing monochrome/missing emojis, so this implementation deliberately does not use it for text shaping.
- **Fail closed for emoji names** until actual server-side color-emoji/RTL/ZWJ screenshots have been reviewed and the caller explicitly sets `colorEmojiVerified:true`. A user-approved transparent PNG overlay is available as a controlled fallback. The fallback image must visually correspond to the channel's exact name; software cannot infer this just from PNG pixels.
- Uniform final-layer alpha `0.15–0.30` dims **both text and colored emoji**, not just the SVG fill. Asset key includes exact full channel name, channel JID, tone, dimensions, style revision and opacity; no cross-channel reuse.
- Single bounded image worker is required in C. Max input 8 MiB, 4 MP, source types JPEG/PNG/static WebP, max reusable layer 1024×256 (~1 MiB raw RGBA), output <= 8 MiB; GIF/video/multipage rejection. Measured in isolated local Node 22 / Sharp 0.34.1: eight flat 3000×2000 jobs ~1.9s / peak ~163 MiB RSS, but eight high-entropy 3000×2000 JPEG jobs ~18.6s / peak ~288 MiB RSS; eight high-entropy 2400×1600 jobs ~11.6s / peak ~251 MiB RSS. A 4000×3000 run peaked ~343 MiB. Accordingly the first-release cap is 4 MP (not an assertion of production 1 GB safety). These are synthetic local results, **not** proof of available memory in MSCC Azure. Real Azure+multi-account RSS requires independent measurement. Quiet-corner positioning is a *heuristic*, not OCR or punchline detection; review mode still gives human placement control.
- `sharp` is **not yet in** `mscc/package.json` at the inspected head. The isolated module intentionally does not modify C's package/lock files. C/maintainer must install, pin, budget and check Sharp under the protected MSCC release workflow before integrating.
- Recommended tests after the dependency exists: `node mscc/channel-branding/caption.test.mjs` and `node mscc/channel-branding/watermark.test.mjs` (the latter requires `sharp`). Development-only visual evidence using Chromium+Noto Color Emoji does **not** certify Skia on the Azure bot host or a WhatsApp-published media item.

## Visual acceptance evidence (development only)

Manually inspected a transparent color-emoji layer and separate dark/light 1200×680 composite fixtures. Both show legible exact Unicode and translucent emoji while leaving the underlying source unobscured; the light fixture is intentionally pale at 25% alpha. **These are Chromium font visual references, NOT screenshots of @napi-rs/canvas on Azure, actual WhatsApp channel publication, or a combined-preview web control.** The target-host `font-visual-probe.mjs` must still be run with approved fonts and its PNGs inspected before enabling emoji output.

## Acceptance before release

Validate nickname `NIGHT 👩🏽‍💻`, mixed RTL/Latin, stylized combining marks, flags, skin tones and long names on the exact host font set. Inspect actual transparent PNG and composited JPEG/PNG/WebP, opacity and position on both dark and light busy images. Check 320/360/390/412 phone preview layouts with A and the real main combined-preview URL after integration. C must test owner separation, OFF/revoke, upload/retry, 1 GB RSS, and unauthorized-channel refusal, then stage separate protected Azure release only with approval.
