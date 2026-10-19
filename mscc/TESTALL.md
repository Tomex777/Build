# NIGHT .testall — offline and live WhatsApp diagnostics

Owner authorization: only the verified supreme owner (isSupremeOwner === true), whether public or private-control. Group admins and sudo users cannot start the audit.

Commands:
- .testall: REAL owner-only WhatsApp command tests in the invoking DM by default; sends actual fixtures and outputs for visual inspection, then reports results. In groups a confirmation is required before any live sends.
- .testall audit: full offline command registry and alias audit, safe local command smoke tests, and regression suites (formerly the bare .testall default).
- .testall live: explicit alias for the DM live tests.
- .testall live confirm (or .testall confirm): mandatory in groups; authorizes many real media messages. No account, admin, member, or moderation mutations.
- .testall failed: retry last failed live cases; in groups add confirm.
- .testall status / .testall cancel: progress and cancellation across offline/live modes.

A real live run exercises actual registered command handlers, not help routes, with explicitly selected safe arguments. It uses a fixed media-fixture suite: deterministic PNG, QR code, 2-page PDF, generated OGG audio and audio-equipped MP4 video, static and animated WebP stickers. Speech transcription uses Azure-generated spoken audio and requires configured Azure Speech credentials; this incurs real provider usage. Media fixtures and results are sent into the SAME WhatsApp chat, and fixture quotes exercise the real WhatsApp media download path.

The harness checks binary signatures, decodes images and PDFs, probes audio/video with FFprobe, then verifies WhatsApp returned an outgoing message ID. Such an ID means WhatsApp accepted the outbound message, NOT that an independent recipient played or viewed the media.

Menus: the Tic-Tac-Toe bot and difficulty pickers are followed automatically in disposable state, with a real game board sent to the chat. Other interactive menus need individual safe cases; their unverified commands must not be labelled as passed.

Both default and explicit live tests are restricted to the explicit allowlist: media, safe utilities/search, cards, and anime sticker reactions. No destructive commands, private control, group membership, permissions, broadcasts, account changes or restarts are executed live. Every omitted public command is listed as NOT LIVE-TESTED. Do not silently expand this allowlist.

Sequential execution and pacing reduce WhatsApp flood and Azure media contention. A failure can mean no output, a returned error, an invalid output format, failed provider/API call, timeout, or WhatsApp rejection. Inspect per-command feedback. Test actual recipient playback separately with an authorized independent test account.

Follow mscc/AGENTS.md release policy: green feature checks do not imply a live Azure deployment. One consolidated release and protected production approval required; verify production and actual WhatsApp behavior after approval.
