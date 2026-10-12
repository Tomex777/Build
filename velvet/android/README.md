# Velvet — native Android

Velvet is a native **Kotlin + Jetpack Compose** Android app, currently being developed as a two-person relationship app. Open `velvet/android` in Android Studio, or build it using GitHub Actions on the `velvet-android-foundation` branch.

## Android requirements

- Minimum SDK: 26 (Android 8)
- Compile SDK and target SDK: 35 (Android 15)
- Stable application ID: `dev.velvet.app`
- Native Compose UI (not an HTML WebView)

## App areas

- **Home**: relationship card, overlapping partner pictures, affectionate gestures.
- **Chat**: full-screen messages, contextual actions, pinned/starred distinction, quoted replies, question card messages, voice-note foundation.
- **Games**: question decks and local game prototypes, with card sharing that stays in Games.
- **Our Story**: Gallery First, Android photo/video picker, albums, timeline, and someday items.
- **Us**: relationship dates, anniversary countdown, profiles, and optional location concepts.

## Builds / stable signing

The workflow **Velvet · Android (stable signing)** compiles a debug smoke test and, **only if both signing secrets are configured**, creates and uploads the installable release artifact named `velvet-permanent-key-signed-apk`.

Add the two credentials as GitHub Actions repository secrets:

- `VELVET_KEYSTORE_B64`
- `VELVET_KEYSTORE_PASSWORD`

See [SIGNING.md](SIGNING.md) for the one-time setup, certificate fingerprint, and upgrade instructions. **Never distribute a debug build as a permanent-key update**. Never commit signing keys, passwords or credentials to git. All future updates must retain the same signing identity and application ID, and increase `versionCode`.

## Not connected yet

Real multi-device messaging, remote heartbeats, notifications, Azure Blob Storage access, Supabase Auth / Realtime, and multiplayer synchronization are still to be integrated. Local preview functionality is **not** a live two-device service.

This branch is independent of other app development branches in the shared Build repository.

## v0.7 native account and pairing groundwork

Home has a **Connect our phones · Pairing** destination using Supabase email/password authentication, email confirmation, short-lived one-time invitations, and a pairing-status display. Refresh tokens are stored via Android Keystore AES-GCM, not plaintext preferences. The client makes only authenticated HTTPS requests and embeds a **public publishable** Supabase key.

The build reads `VELVET_SUPABASE_URL` and `VELVET_SUPABASE_PUBLISHABLE_KEY` from CI Actions *Variables* (or environment variables locally). If absent, the pairing screen clearly reports that the backend is unavailable and does not pretend to have paired anything.

⚠️ Pairing is **not live yet**, because no Supabase project/migrations were deployed through this workspace. Even when activated, this milestone shares *membership state only*: existing chat messages, games, media and music are still local demos. Do not interpret their UI as two-phone synchronization. See `../backend/README.md` for the deployment order and SQL security tests.

## v0.8 alpha — Paired two-phone text messaging (backend activation required)

A separate native **Connected chat** screen now appears after two accounts complete pairing. It uses the authenticated `velvet_send_text` and `velvet_list_text` RPCs, polls only when the chat is open (~3 seconds), and retries idempotently from an app-private, account-and-couple-scoped outgoing queue. The existing **Chat tab** remains an offline preview and is never silently uploaded to the server.

This feature is **not enabled in pre-configured builds**. CI can produce a signed APK, but two phones will not exchange data until the SQL migrations (including `20261011000004_velvet_text_chat.sql`) are deployed to a real Supabase project, and the publishable URL/key are embedded by CI variables. It is not push messaging, end-to-end encryption, or a fully offline-synchronized history. Media, voice, games and calls remain preview-only.

After activating, test on two Android phones: sign up + verify each account → generate and redeem a code → open Connected chat on both devices → send each direction → temporarily disable Wi-Fi, queue a message, reconnect → verify it sends exactly once → reopen to verify server history.

## v0.9 alpha — Chat tab integration, replies, and older history

The **Chat** tab automatically checks whether its active account belongs to a verified two-person couple. If paired, it opens the **connected text conversation**. If unpaired or unconfigured, it keeps the familiar **local prototype**. Connected chat has an explicit **Local preview** action; old device-local chat, voice recordings, games and question cards are never secretly uploaded into the live conversation.

In connected text chat, tapping or holding a bubble selects a quoted reply. The composer displays the selected message and saves its stable message UUID in the offline outbox. If a message cannot be sent, the user may explicitly **Discard retry**, which removes only the local unsent retry. History can be paged backwards in 80-message blocks from the server, while the screen polls forward every ~3 seconds when visible. Data is sorted with a stable server (timestamp, UUID) cursor to avoid duplication and skipping tied timestamps.

**Offline-boundary details:** A queued message is retried only while Connected chat is open; this is not Android background work or push delivery. Restoring a session and verifying two-person membership currently requires internet. If it cannot be checked, the user sees the local demo with an explicit connection status rather than a false synced-chat promise. Replies from the offline question deck remain *offline preview* content. There is no E2EE or push notifications yet.

To activate on a real project, apply the additional `20261011000005_velvet_history.sql` migration and rebuild with the same public Supabase project configuration; CI executes the cursor and authorization tests against disposable PostgreSQL. This is an unverified phone-to-phone feature until a real backend has been deployed and two physical devices have exchanged messages successfully.

## v0.10.0 — Owner-controlled account profiles

Migration `20261011000006_velvet_profile_sync.sql` introduces two authenticated RPCs. `velvet_get_my_profile()` retrieves **only the caller's** display name and pronouns, and `velvet_update_my_profile(p_display_name,p_pronouns)` modifies **only the caller's** profile. A confirmed account is required for writes. Names are trimmed and limited to 50 characters; optional pronouns are limited to 32 characters. Callers cannot supply a user ID or alter the partner's profile, avatar blob key, or pair membership through these functions.

The existing `velvet_my_pair()` response is expanded without removing previous fields: `own_name`, `own_pronouns`, `partner_name`, `partner_pronouns`. Client profile mutation privileges are revoked in favor of narrowly scoped RPCs. This does **not** enable cloud avatar uploads (still requires restricted media infrastructure).

`velvet/backend/tests/profiles.sql` verifies owner-only changes, validation, read-only partner access, unpaired-user isolation, confirmation requirements, revoked direct SQL writes, and anonymous denial; GitHub Actions now executes those tests.

**Android:** In **Us**, tap **Your profile** to edit your display name and optional pronouns. Changes are first saved locally, then sent to the user's authenticated account. Velvet displays whether sync completed or needs retry. Unsynced local edits are never overwritten by server fetches; on an account-connected device with no local unsynced edit, the authenticated profile is loaded on entering Us. The partner's profile remains view-only. When available, the partner's name is read from their account profile rather than silently rewriting it through a local nickname.

**Activation:** Apply all six migrations in filename order to a real Supabase project, keep email confirmation enabled, and rebuild with the HTTPS project URL and public publishable key in CI variables. There is **no active Supabase project** on the connected account at the time of this change. Server tests alone do not prove two-phone behavior.

## v0.11.0 — Us: shared anniversary

The Us screen's anniversary picker now has an account-backed update path through `velvet_set_anniversary`. Local edits are retained on this device when offline/unpaired; **Share date with my partner** explicitly retries when needed. On a paired device without a pending local edit, the shared date is loaded from the server. The database records who last edited this shared field.

This is distinct from private profiles: nobody can edit the other person's name or pronouns. Neither location nor profile photos are shared by this milestone. Check `../backend/README.md` for the `20261011000007_velvet_shared_anniversary.sql` migration and activation steps. The connected account still has **no Supabase project**, so live two-phone delivery remains unverified.

## v0.12.0 — Connected text message editing and deletion

Migration `20261011000008_velvet_message_actions.sql` adds narrowly authorized functions `velvet_edit_text(p_id,p_body)`, `velvet_delete_text(p_id,p_for_everyone)` and `velvet_list_text_changes(p_after_id,p_limit)`. The sender may edit a text message within **10 minutes**, or delete it for both members within **48 hours**. Either partner may **Delete for me** at any time: that action hides the message for only the calling account. Edits cannot mutate sender, couple, reply reference, time or other privileged columns; direct table updates remain disallowed.

Delete-for-everyone erases the text body in the database and retains a textless tombstone for both partners and quoted replies. Historical and initial text fetches now include those tombstones and exclude the calling user's personally hidden messages. A private, append-only change feed informs an already-open chat about older text edits, global deletions, and its own personal hides without requiring a full chat reload. Changes are sequentially created behind couple-row locks so an authenticated partner's cursor does not miss an earlier uncommitted mutation. Anonymous and unrelated users cannot read changes.

Android uses a **long press** on a delivered text bubble to open message actions, with separate **Edit**, **Delete for me**, and **Delete for everyone** options (subject to server-enforced ownership and time windows). Updates propagate to the partner on the next foreground polling cycle, not via push. Deleting for me never deletes the partner's copy; deleting for everyone never exposes the erased text. A reply to a deleted text shows the deletion placeholder, not the original text.

Tests in `velvet/backend/tests/message_actions.sql` exercise sender ownership, outsider isolation, separate deletion semantics, tombstones and erased bodies, event cursor, expiry windows, anonymous restrictions and inability to update database tables directly. These are PostgreSQL CI security tests; two-phone behavior is **not verified** until a Supabase backend is connected and tested with real devices. This remains **not end-to-end encrypted**, and neither voice nor photos are synced.

## v0.13 · connected image messages

The connected Chat composer now has a **photo attachment** button. Android's system document/image picker provides a content URI; the app validates the format and enforces an **8 MiB maximum** before uploading the original image binary directly to the authenticated, RLS-protected private Storage bucket. It then calls `velvet_send_image` to publish a scoped message. A caption and optional quoted reply are supported, though media uploads are online-only; a failed upload can be selected again. An image is never published as an unrestricted URL.

Incoming photo bubbles request bytes with the signed-in user's access token and display bounded bitmap thumbnails; tapping opens an immersive, full-screen image viewer. Image metadata pages together with text and participates in the existing **Delete for me** / **Delete for everyone** actions. Photos are **not** automatically saved to Our Story. GIFs currently render as a static first-frame preview.

There is **no end-to-end encryption**, offline media outbox, background media transfer, automatic Storage cleanup, or cloud media gallery yet. Actual Storage HTTP behavior and real-device sharing remain unverified until a real Supabase project is connected. This is an alpha milestone, not a production-ready encrypted messenger.

## v0.14 — Full-screen Chat Info, shared gallery and message search

**Tap your partner's name at the top of connected Chat.** Velvet opens a dedicated full-screen Chat Info screen, not a narrow modal. Its working **Photos** tab shows authenticated, private Chat-only images in a three-column gallery, in newest-first order, paginating twelve items at a time. Gallery previews decode at reduced resolution for low memory use, and tapping any image opens the higher-resolution full-screen viewer (with thumbnail fallback when offline).

The working **Search** tab looks for literal text in this paired conversation and photo captions (2–64 characters, case-insensitive), with newest-first results and pagination. Results are shown as excerpts with sender and date; tapping results to jump into old messages is **not implemented yet**. The working **Links** tab uses the same paired-only search endpoint to find HTTP/HTTPS URLs in messages and photo captions, shows who shared each link and when, and asks for confirmation before opening an external browser. Documents, voice recordings and pinned-message lists are **not implemented online yet**; the interface explicitly labels those pending.

The backend excludes deleted and personally hidden images from new gallery queries. Nothing is copied from Chat into Our Story without an explicit user-directed feature in a later milestone. Online operation requires deploying migrations **01–10** and configuring a real Supabase project's public key. It is not end-to-end encrypted. Real two-device media and gallery behavior are not yet verified.

### v0.14.1 reliability fix — offline session checks

An Android phone with an encrypted saved sign-in no longer silently treats a temporary network outage or Supabase HTTP 5xx as an account sign-out. Only a recognized revoked/expired refresh token invalidates the local session; transient failures keep the encrypted credential available for retry. The Chat gateway shows its recoverable connection banner rather than implying an authenticated conversation has disappeared. The pairing screen and connected Chat catch session-restore failures instead of allowing an uncaught coroutine exception. There is still no offline cache of cloud message history or realtime background delivery.

## v0.15 — Read ticks and connection-safe Chat entry

Connected Chat now acknowledges only partner-authored bubbles present in the visible list while the activity is STARTED and the user is **not** browsing Chat Info. The sender sees **✓ Sent** for server-saved texts/photos and **✓✓ Read** after their partner explicitly acknowledges seeing that point in the conversation; offline text messages remain labeled **Queued**. These are not delivery acknowledgments, and the foreground poll frequency remains approximately 3 seconds, with no push notifications or end-to-end encryption.

If Velvet cannot check an existing online account during an outage, the Chat gateway no longer silently loads a local preview that might be mistaken for a real partner conversation. It instead shows a recoverable connection screen and requires explicitly selecting the offline demo. Unpaired/unconfigured users see a prominent LOCAL PREVIEW label. Online testing still requires a real Supabase deployment with migrations 01–11.

## v0.16 — Record, preview, send and play real voice messages

Connected Chat now has a microphone recording control. Android asks for `RECORD_AUDIO` only after a user taps the microphone. Capture uses Android's native MediaRecorder in **MPEG-4/AAC (M4A)** format. The interface shows a live amplitude-based waveform, elapsed time, Stop and Discard buttons. After stopping, the draft can be **previewed, replayed, cancelled or explicitly sent**. A recording is never uploaded while merely recording or previewing. Microphone capture is discarded on backgrounding, when the composer leaves the screen, or on a failed/too-short recording. Drafts last up to 120 seconds and 5 MiB.

Voice upload uses the authenticated private `velvet-chat-voice` Storage bucket, followed by the `velvet_send_voice` RPC. Conversation history and older pages now include voice messages with waveform and duration metadata. Playback loads the private file using the signed-in user's access token into app-private cache, with Play/Pause, waveform, and scrubbing. Cached audio is deleted on player disposal. Sender-scoped delete actions, quoted replies and read receipts also apply to voice messages.

This is an **alpha**, with no end-to-end encryption, outgoing offline voice queue, media compression options, background transfer, push notifications or secure cloud media erasure. Voice is not automatically shared with Our Story. Connected audio cannot be tested between real devices until a Supabase project is configured and migration 12 deployed.

## v0.17 — Audio gallery and polished connected composer

Tapping your partner's name now opens Chat Info with four sections: **Photos · Audio · Links · Search**. The Audio tab lists real server-backed voice messages with sender attribution, timestamps, duration, and private authenticated playback. Voice history loads in batches of 16 with a bounded older-history cursor, explicit refresh and retry UI. Switching away from Audio disposes its player components; playback also pauses when the activity goes to the background, and choosing another recording pauses the previous one.

The connected Chat composer now uses a single row with photo attachment, text field, and a trailing action that changes between **microphone and send**. While recording or previewing a voice draft, the recording UI replaces the text editor without resetting the active MediaRecorder. The user still explicitly previews or sends audio, rather than uploading on record.

All voice media stays separate from Our Story. Real paired-device verification still depends on a linked Supabase project, and media transport is **not end-to-end encrypted**. This release does not yet implement realtime WebSockets, offline media outbox, voice transcription, or push notifications.

## v0.18 — Protected MP4 clips in Chat + Video gallery

Connected Chat's attachment paperclip opens a short picker for **Photo** and **Video** rather than launching only a photo picker. The Video option accepts `video/mp4` clips under **20 MB and two minutes**, using Android's document/content provider and `MediaMetadataRetriever` to validate duration locally. Selected clips are sent to private Storage and explicitly finalized as a chat message with optional caption and quote. No public image/video URLs are exposed; the existing foreground chat feed and read receipts also handle videos.

Each video message has a tappable video card with duration, and an **immersive full-screen video viewer**. Downloading starts only after tapping the clip. The authenticated MP4 streams to an **app-private temporary file**, with built-in Android video playback controls; the file is removed when the viewer closes. Backgrounding pauses playback. The connected Chat Info header now has **Photos · Videos · Audio · Links · Search** tabs in a scrollable row. The Videos tab provides sender attribution, captions, full-screen playback, refresh and older-media pagination. Chat-only clips are never automatically added to Our Story.

This is an **alpha**: no video compression, 4K transcoding, upload progress meter, resumable offline uploads, background transfers, E2EE, media-sharing push notifications, or production-device pairing test. Configure Supabase and apply migrations 01–15 before expecting two real devices to exchange these files.

## v0.18.1 — Media lifecycle reliability

Video downloads now have caller-owned temporary files, including cancellation before the IO dispatcher can return a result. Closing a viewer cancels the download and removes its private file; bounded download loops check coroutine cancellation. Video and voice playback do not auto-start after a download finishes while the activity is in the background. Voice cancellation releases the player and removes the draft cache. Unsupported/corrupt video playback shows a retry action instead of an empty viewer. Full-screen video controls respect system-bar and display-cutout insets. Pairing copy describes the connected media features currently implemented.

Release acceptance still requires physical-device checks: close a video while downloading, background during voice/video loading, retry a corrupt MP4, and check gesture-navigation/cutout layouts. No real Supabase project is configured, so paired-device delivery, notifications, Our Story sync, online games and E2EE remain unfinished.

## v0.19 — Connected play and explicitly saved Our Story

Games → Online lounge has two-phone Tic-tac-toe and Connect Four rounds with server-enforced turns, board rules, win/draw detection, version checks and rematches. Updates poll every three seconds while the screen is foregrounded. Up to five active rounds are permitted; interrupted round creation reuses its UUID. Local board games remain separate.

Long-press a connected Chat photo/video → Save to Our Story. Our Story → Shared Story lists only explicitly curated media, with original sender and saver attribution. Saving is idempotent and the first saver is retained. The saver can remove the shared entry without modifying Chat. Saved media remains accessible independently of Chat deletion/hiding until removed from Story. No automatic copy takes place.

Requires the connected_games_and_story migration after migrations 01–15. Live two-device operation remains unverified until backend activation. No push notifications, realtime WebSockets, online Chess/Ludo/Draw, cloud uploads directly from the local Story gallery or end-to-end encryption are implemented by this release.
