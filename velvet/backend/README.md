# Velvet backend contract — not deployed

Android's **0.6.0 alpha stores demonstration and newly composed messages in a local SQLite cache**. Installing the APK does not connect to Supabase, Azure, FCM, or a second phone.

Stack selected: Supabase Auth / PostgreSQL / Realtime (authorized private channels), Azure private Blob Storage, Firebase Cloud Messaging, and Room for offline caching.

## Deploy sequence (after configuring your own accounts)

1. Create a Supabase project, back up credentials securely, and apply the SQL migration with the Supabase CLI.
2. Add trusted invitation/pairing functions; membership is deliberately denied for direct client edits. Sign up each person separately. Restrict each couple to two people with a database trigger.
3. Provision a **private** Azure container, and generate short-lived, single-object SAS tokens inside a protected backend endpoint. **Never** place storage account keys or privileged Supabase keys in an APK or Git repository.
4. Register Android with Firebase Cloud Messaging; protect token registration per user and send notifications only from trusted server functions. Restrict message previews for sensitive content.
5. Build local Room sync, conflict handling, deletion tombstones, starred-message per-user state, pinned-message shared state, and read cursors.
6. Validate game moves server-side, preserve original sender attribution in chat files, and record who saved an attachment to Our Story.

## Data semantics

- `messages.kind=question_card`, `payload` has card ID, category, palette key; no JPEG screenshot necessary. Sending a card does not navigate away from Games.
- `messages.deleted_at` is a tombstone for **Delete for everyone**; `hidden_messages` is **Delete for me**. Neither deletion is an authorization bypass.
- `messages.pinned_at` is a shared pinned banner; `stars` are per-user bookmarks.
- `media` is a storage reference; `message_media` and `story_entries` are **separate logical libraries** and can reference the same blob without duplication. `uploaded_by` and `added_by` are distinct.
- Heartbeats are not chat messages. Use short-lived authorized realtime events while online; background push must respect OS limitations and user opt-in.
- A security audit is required before production. Database RLS is one layer; trusted endpoints must also verify ownership, rate limits, scoped SAS and message/reply references.

## Android 0.6.0 preview boundaries

The newest preview contains **offline** question decks, editable personal decks, a two-answer reveal mode,
local/pass-and-play Tic-Tac-Toe, Connect Four, Chess, Ludo and Draw & Guess, a private media
selection/preview flow, Our Studio, and Spotify Jam invitations/song dedications.

- Spotify content **never** streams through our APK. A Jam starts in Spotify. Remote participants
  require individual Premium eligibility; Velvet only stores approved links and the notes users write.
  The actual Spotify listening session, controls and audio remain in Spotify. Android deep-links
  launch the official Spotify destination or browser. We do **not** manufacture Jam links.
- Music notifications, partner auto-join, two-phone question reveals, remote game moves, multi-device
  media sharing, and live voice/video calling are **not active** until authentication, pairing, trusted
  server endpoints, notifications and media infrastructure are deployed.
- Custom question decks are authored manually on-device. Server-side optional AI generation would
  produce entire drafts only **by request**, with editorial review and semantic deduplication prior
  to inclusion. No generation runs on each swipe. No AI credentials are embedded in this app.
- 174 new human-written questions are bundled with the earlier starter questions. They are
  curated for variety, but lexical screening is not a mathematical uniqueness guarantee. Later
  packs should receive intent labeling, embedding-based checking and human spot reviews.
- Chat attachments selected through Android's privacy-preserving photo picker / system file picker
  are copied to local private storage. Video files can be previewed. **They do not upload or sync
  to the other person's phone yet.** Chat media is separate from curated Our Story.
- Board games are local versions. Online match validation must happen inside trusted endpoints,
  not through client-side state writes.

The migrations in this folder are a *design contract*, not evidence of a deployed backend.

## Pairing milestone — code completed, deployment not yet available

Migration `20261011000003_velvet_pairing.sql` introduces a verified-account, two-person handshake:

- `velvet_create_pair_invite()`: creates a private couple if necessary and returns a **single-use, 80-bit invitation code** (20 hexadecimal characters, grouped for easier entry), valid for 20 minutes.
- `velvet_join_pair_invite(p_code)`: consumes the token atomically, admits only a distinct, verified second account, and enforces a 10-attempt-per-hour per-account limiter.
- `velvet_my_pair()`: returns only the authenticated user's membership state and (when paired) their partner's existing profile name.
- `velvet_revoke_pair_invite()`: invalidates the unused code. Issuing a new code automatically supersedes the old code.

Only salted? **No:** only a SHA-256 digest of the high-entropy token is stored. The token itself is returned once. The two sensitive tables have RLS enabled **without any client policies**. Only explicitly granted, carefully scoped membership-checked functions operate on them.

`velvet/backend/tests/bootstrap.sql` sets up a disposable PostgreSQL-only Auth stub, and `velvet/backend/tests/pairing.sql` verifies two-person membership, redemption replay prevention, third-party RLS isolation, direct-write denial, rate limiting, verified-account requirement and anonymous RPC denial. CI runs these tests against a fresh PostgreSQL 16 service.

**Deployment boundary:** No Supabase project is connected to this workspace yet; these migrations are only in GitHub. Nothing can synchronize between phones until a project is provisioned, all migrations are applied, API variables are configured, and Android builds are rebuilt. An invite code is NOT a message encryption key. Authentication and pairing alone do not turn the local Chat/Games previews into live synchronization.

### Activation (administrator)

1. Provision a dedicated Supabase project with email confirmation enabled. Apply the existing migrations in filename order through the Supabase CLI; run the pairing security tests against a disposable database beforehand.
2. In repository Actions **Variables** (not signing secrets), configure `VELVET_SUPABASE_URL` (the HTTPS project URL) and `VELVET_SUPABASE_PUBLISHABLE_KEY` (the public `sb_publishable_...` key). Never use a `service_role`, `sb_secret_...`, or database password.
3. Rebuild the signed Android APK; open Home → **Connect our phones · Pairing**. On phone A create and verify an account, sign in, generate a code. On phone B sign in separately and redeem the code. Tap **Refresh pairing status** on A.
4. Confirm both accounts see two members before building messaging. Rate-limit invites at the perimeter as a second layer; obtain a professional security audit before a production launch.

On sign-out, Android deletes the locally encrypted refresh token. The token was stored using the non-exportable Android Keystore AES-GCM key. Device-only local preview messages remain separate; they are NOT imported into a shared conversation.

## v0.8.0 — Connected text chat contract (not deployed)

Migration `20261011000004_velvet_text_chat.sql` revokes client-side table writes and exposes two intentionally scoped SQL RPC endpoints:

- `velvet_send_text(p_id, p_body, p_reply_to)` validates authentication, the caller's authenticated membership, exactly two members in the couple, 1–4000 characters, and reply ownership. Messages receive a client-generated UUID for **idempotent retry**; a retry with altered content is rejected. 40 sends per user per minute is the database-side safety ceiling.
- `velvet_list_text(p_after_at, p_after_id, p_limit)` returns at most 100 text messages in chronological order, initially taking the newest 80. Later requests use a stable (server timestamp, UUID) cursor. It returns nothing for users without a completed two-person pairing.

The previously broad direct `velvet_messages` client INSERT/UPDATE/DELETE permissions are explicitly revoked, closing an existing mass-assignment risk; updates, reactions, pins, media, and deletes await separately audited endpoints. Neither the mobile client nor its SQL calls select a couple ID directly.

**Android:** Home → **Connect our phones** → sign in/pair → **Open connected chat**. The text-only screen refreshes the server every ~3 seconds **while open** (foreground polling, not Realtime WebSockets or background delivery). It queues unacknowledged text locally under the *specific user + couple IDs*, preserving the same UUID on every retry. This is not end-to-end encryption. The user has no indication that a partner read a message yet.

**Not included:** encrypted end-to-end messaging, push notifications, presence/typing, synchronized preview Chat history, media uploads, game state synchronization, offline history pagination, background delivery, websocket subscriptions. Do not market this as full realtime/push messaging until those milestones are actually delivered.

**CI:** `velvet/backend/tests/messages.sql` runs with a disposable PostgreSQL Auth fixture, after the pairing tests, and proves cross-account exchange, cursor fetching, idempotent retry, outsider isolation, direct-write denial and 40/min rate limiting.

**Activation:** Apply *all* four migrations in filename order to a dedicated Supabase project, enable verified email/password auth, and set both public Android build variables described above. The current connected Supabase account has no project configured; the live feature cannot be exercised yet. Validate two physical phones before relying on this implementation.

## v0.9 historical chat pagination

Migration `20261011000005_velvet_history.sql` adds `velvet_page_text(p_before_at,p_before_id,p_limit)`. It reads only the authenticated caller's **fully paired** conversation and selects at most 100 earlier text messages per call, using a strict **before** tuple comparison on server timestamp + UUID. The result is chronological. The existing `velvet_list_text` remains the **after** cursor used for foreground polling, and no direct client `SELECT` privilege has been granted.

The backend test suite now includes `velvet/backend/tests/history.sql`, verifying two-page ordering, no repeated boundary records, input bounds, cross-couple isolation and denied anonymous access. This works with PostgreSQL CI fixtures; there is no live Supabase project in the connected account.

## v0.10.0 — Owner-controlled account profiles

Migration `20261011000006_velvet_profile_sync.sql` introduces two authenticated RPCs. `velvet_get_my_profile()` retrieves **only the caller's** display name and pronouns, and `velvet_update_my_profile(p_display_name,p_pronouns)` modifies **only the caller's** profile. A confirmed account is required for writes. Names are trimmed and limited to 50 characters; optional pronouns are limited to 32 characters. Callers cannot supply a user ID or alter the partner's profile, avatar blob key, or pair membership through these functions.

The existing `velvet_my_pair()` response is expanded without removing previous fields: `own_name`, `own_pronouns`, `partner_name`, `partner_pronouns`. Client profile mutation privileges are revoked in favor of narrowly scoped RPCs. This does **not** enable cloud avatar uploads (still requires restricted media infrastructure).

`velvet/backend/tests/profiles.sql` verifies owner-only changes, validation, read-only partner access, unpaired-user isolation, confirmation requirements, revoked direct SQL writes, and anonymous denial; GitHub Actions now executes those tests.

**Android:** In **Us**, tap **Your profile** to edit your display name and optional pronouns. Changes are first saved locally, then sent to the user's authenticated account. Velvet displays whether sync completed or needs retry. Unsynced local edits are never overwritten by server fetches; on an account-connected device with no local unsynced edit, the authenticated profile is loaded on entering Us. The partner's profile remains view-only. When available, the partner's name is read from their account profile rather than silently rewriting it through a local nickname.

**Activation:** Apply all six migrations in filename order to a real Supabase project, keep email confirmation enabled, and rebuild with the HTTPS project URL and public publishable key in CI variables. There is **no active Supabase project** on the connected account at the time of this change. Server tests alone do not prove two-phone behavior.

## v0.11.0 — Shared anniversary (requires backend activation)

Migration `20261011000007_velvet_shared_anniversary.sql` adds `velvet_get_anniversary()` and `velvet_set_anniversary(p_date)`. Only a signed-in, verified member of a **fully paired two-person space** may set a shared relationship start date. The new value is validated (not future and not earlier than 1900), and the couple row is locked for serialized updates. Either partner can correct the shared date; the DB tracks which account changed it and when. Unpaired accounts, unverified accounts, and anonymous callers cannot change it. Direct client `velvet_couples` updates remain denied.

`velvet/backend/tests/anniversary.sql` verifies creator and partner reads, both-member updates, last-edit attribution, invalid dates, direct-write denial, outsider isolation, and anonymous denial. GitHub Actions executes it after pairing, messaging, history and profile tests.

The Android Us tab saves date selections locally before attempting to share them. A previously device-only date is **not silently uploaded**; there is a "Share date with my partner" control. If remote sharing fails, the local date is kept and can be retried. If there is no unsynced local edit, a paired authenticated device reads the shared date on opening Us. No remote date synchronization is possible until the migrations have been applied to a real Supabase project.

## v0.12.0 — Connected text message editing and deletion

Migration `20261011000008_velvet_message_actions.sql` adds narrowly authorized functions `velvet_edit_text(p_id,p_body)`, `velvet_delete_text(p_id,p_for_everyone)` and `velvet_list_text_changes(p_after_id,p_limit)`. The sender may edit a text message within **10 minutes**, or delete it for both members within **48 hours**. Either partner may **Delete for me** at any time: that action hides the message for only the calling account. Edits cannot mutate sender, couple, reply reference, time or other privileged columns; direct table updates remain disallowed.

Delete-for-everyone erases the text body in the database and retains a textless tombstone for both partners and quoted replies. Historical and initial text fetches now include those tombstones and exclude the calling user's personally hidden messages. A private, append-only change feed informs an already-open chat about older text edits, global deletions, and its own personal hides without requiring a full chat reload. Changes are sequentially created behind couple-row locks so an authenticated partner's cursor does not miss an earlier uncommitted mutation. Anonymous and unrelated users cannot read changes.

Android uses a **long press** on a delivered text bubble to open message actions, with separate **Edit**, **Delete for me**, and **Delete for everyone** options (subject to server-enforced ownership and time windows). Updates propagate to the partner on the next foreground polling cycle, not via push. Deleting for me never deletes the partner's copy; deleting for everyone never exposes the erased text. A reply to a deleted text shows the deletion placeholder, not the original text.

Tests in `velvet/backend/tests/message_actions.sql` exercise sender ownership, outsider isolation, separate deletion semantics, tombstones and erased bodies, event cursor, expiry windows, anonymous restrictions and inability to update database tables directly. These are PostgreSQL CI security tests; two-phone behavior is **not verified** until a Supabase backend is connected and tested with real devices. This remains **not end-to-end encrypted**, and neither voice nor photos are synced.

## v0.13 · private chat photos (Storage integration)

Migration `20261011000009_velvet_private_images.sql` creates a **private** Supabase Storage bucket `velvet-chat-media` (maximum file size 8 MiB; JPEG/PNG/WebP/GIF) and RLS policies that enforce authenticated, fully paired access. Uploads use **<couple UUID>/<sender UUID>/<random UUID>.<ext>**; another user cannot upload to someone else's folder. Authenticated downloads require a real, non-deleted image message belonging to the signed-in member's couple and not hidden for that member. The `velvet_image_is_visible` SECURITY DEFINER helper performs this narrowly scoped check without granting unrestricted `SELECT` on `velvet_messages`.

The `velvet_send_image` function verifies ownership of the uploaded Storage object before registering an image message, validates MIME, extension, byte size, caption, and reply reference; rate limits sending and supports retry-safe message UUIDs. The permanent `velvet_image_claims` table prevents recycling an uploaded path after its message has been deleted. New mixed-message pagination RPCs `velvet_list_chat` and `velvet_page_chat` include text **and** image messages in chronological order, with metadata only (not embedded binary blobs). The original text-only RPCs remain for older Android builds. Image deletion uses the same sender-scoped 48-hour delete-for-everyone policy and immediate per-user hide as text; global deletion removes caption, image path and all message payload while retaining a non-revealing tombstone.

**Storage lifecycle limitations:** Upload bytes are encrypted in transport over TLS but are **not end-to-end encrypted**. Deleted or unsent images are not automatically purged from Supabase Storage; Storage object deletion MUST later use the Storage API from a trusted server/Edge Function rather than manipulating `storage.objects` directly. The current milestone denies subsequent reads through RLS, but secure erase and orphan cleanup are not yet deployed. Chat photos remain separate from **Our Story**; nothing is copied automatically. Image loading is authenticated, never a publicly accessible URL.

`velvet/backend/tests/images.sql` runs using a disposable PostgreSQL simulation of Storage's metadata/RLS. That verifies private upload restrictions, member-only reads, owner-only publication, no double-send/reuse, caption and MIME checks, partner-only hiding, and global tombstone behavior. It does **not** emulate actual upload-binary APIs; end-to-end two-phone testing requires a real Supabase project with migrations **01–09** deployed.

## v0.14 — Scoped Chat Info photo gallery + conversation search

Migration `20261011000010_velvet_chat_info.sql` adds two authenticated, read-only RPCs, granted **only** to `authenticated`:

- `velvet_chat_photos(p_before_at,p_before_id,p_limit)` serves the newest non-deleted photo messages for the signed-in member's **current fully paired couple**, excluding that member's personally hidden messages. Results contain sender ID, caption, creation time, MIME and private Storage path, not binary bytes. Keyset history pagination uses `(created_at,id)`, maximum 60 items per page.
- `velvet_search_chat(p_query,p_before_at,p_before_id,p_limit)` searches literal (not SQL-wildcard) case-insensitive message text and photo captions, between **2 and 64 characters**. It hides deleted and personally hidden messages and never exposes another couple's history. The same bounded keyset pagination applies.

The gallery queries `velvet_messages` only: Chat photos remain separate from `velvet_story_entries` and are **never automatically copied to Our Story**. A per-couple partial image index supports chronological photo history.

`velvet/backend/tests/chat_info.sql` tests member authorization, the partner's visibility, anonymous/outsider denial, photo history pages, literal % and _ matching, case-insensitive captions, search pagination, malformed cursors and immediate exclusion of deleted or personally hidden content. The backend CI applies **all ten numbered migrations (01–10)** and runs these checks in PostgreSQL. A production Supabase Storage backend is still not connected; CI does not validate real-device media delivery.

## v0.15 — Foreground visible-message read acknowledgments

Migration `20261011000011_velvet_read_receipts.sql` introduces `velvet_read_cursors` and restricted `velvet_mark_chat_read(p_message_id)` plus `velvet_chat_read_state()`. Read cursors are **per member and per couple** and are monotonic in server message order `(created_at,id)`. The server checks that the target is a **partner-authored, non-deleted, not-personally-hidden** text or image message belonging to a fully paired relationship. No client can write someone else's cursor, supply an arbitrary couple ID, or read an outsider's state.

Connected Chat checks its **actually visible message bubbles** while the screen is foregrounded. Fetching history alone doesn't produce read receipts, and opening Chat Info doesn't mark messages read. Acknowledgment requests happen during its existing foreground-only 3-second polling loop; a sender sees **✓ Sent** for server-saved messages and **✓✓ Read** only once the partner's authenticated acknowledgment cursor covers that message. Queued offline texts show **Queued** rather than a fabricated delivered/read status. These are **read**, not delivery, receipts; there is no realtime push, background service or per-message notification yet.

`velvet/backend/tests/read_receipts.sql` verifies ownership, initial state, monotonic retries, image read acknowledgment, forbidden own-message and hidden/deleted reads, unauthenticated denial and direct-table-write denial. A disposable PostgreSQL CI run validates these contracts; actual two-device operation remains unverified until Supabase is connected.

## v0.16 — Connected private AAC voice notes

Migration `20261011000012_velvet_voice_notes.sql` creates a distinct **private** Supabase Storage bucket `velvet-chat-voice` for AAC/M4A messages (up to 5 MiB). Authenticated storage uploads require a path `<couple-id>/<own-user-id>/<random-id>.m4a` and exactly two members in the current couple; anonymous and outsider uploads are denied. Authenticated downloads require a corresponding non-deleted, non-personally-hidden `voice` message owned by that couple. No client can update or delete Storage objects. Voice message claims remain after tombstones, preventing reusing a deleted audio path.

The restricted `velvet_send_voice` RPC checks membership, uploaded object ownership, uniqueness, attachment bytes (1–5 MiB), duration (0.5–120 seconds), waveform levels (up to 42 samples, integers 0–100), and an optional valid couple-scoped reply. It is idempotent for the same message UUID and payload. Existing mixed-chat pagination and scoped delete/read-receipt APIs now include voice messages. They remain protected from cross-couple calls.

The `velvet/backend/tests/voice_notes.sql` integration suite checks storage upload/read RLS, forged paths, pending versus published attachment access, sender/partner rights, personal hide, global delete, duplicate publication, invalid waveform rejection and deletion path recycling. These are **database-level tests only**, using test Storage metadata and not real audio bytes. For actual playback, a real Supabase deployment is required.

**Limitations**: Audio is transmitted over TLS but **is not end-to-end encrypted**. Deleted attachments and failed uploads still need a trusted Storage cleanup worker. There is no background media outbox or notifications yet. Voice notes belong to **Chat**, not to Our Story.

## v0.17 — Pair-private Chat Info Audio library

Migration `20261011000013_velvet_chat_audio_gallery.sql` adds `velvet_chat_audio(p_before_at,p_before_id,p_limit)`, a read-only authenticated RPC with a bounded keyset cursor. It returns only **voice messages** in the caller's currently paired couple, excluding deleted recordings and messages personally hidden from that member. Returned metadata includes sender ID, creation time, voice duration, waveform, private Storage path and MIME type. No public URLs, media bytes, or cross-couple results are returned. A partial chronological voice index supports history pagination.

`velvet/backend/tests/chat_audio_gallery.sql` verifies chronological 2+2 pages without gaps, cursor termination, MIME/metadata, member visibility, personal hides, sender-only global deletes, malformed cursors, a second fully paired couple's isolation, and anonymous refusal. CI runs it after the voice notes tests. Gallery results are a view of **Chat attachments only** and never insert into Our Story.

Actual voice playback on two phones is not verified until a live Supabase project and all migrations through 13 are deployed.

## v0.18 — Connected private video messages and shared videos

Migrations `20261011000014_velvet_video_chat.sql` and `20261011000015_velvet_video_gallery.sql` create a **private** `velvet-chat-video` Storage bucket (maximum **20 MiB**, accepted MIME `video/mp4`), immutable video-to-message claims, authenticated per-couple Storage INSERT/GET policies, and the `velvet_send_video` RPC. Video publication requires an authenticated account paired with exactly one partner, an uploaded object owned by the message sender, a scoped folder path, a 0.1–120 second duration, and an optional caption (up to 1,200 characters). Retried exact-ID sends are idempotent; copied object paths and crossed couples are rejected. Existing chat cursors, delete-for-me/delete-for-everyone, and read-receipt RPCs now include `kind='video'`.

`velvet_chat_videos` is a **read-only, authenticated, cursor-paginated** private Chat Info query that excludes globally deleted and personally hidden videos. It does not copy any video to Our Story. Its result contains only metadata and private Storage paths, never public links. Dedicated integration suites `video_chat.sql` and `video_gallery.sql` cover upload authorization, unpublished-object protection, publisher idempotence, partner GET, hides/deletes, read marks, pagination, and denial to unrelated couples/anonymous users.

Limitations: The original MP4 is uploaded **without transcoding**; larger files or unsupported formats are rejected. Video downloads happen on demand, and failed uploads are not queued offline. Supabase Storage cannot physically purge old uploads until a separate trusted cleanup worker is deployed. **Media is encrypted in transit, not end-to-end encrypted.** Backend tests use simulated Storage metadata; real two-device video delivery requires a linked Supabase project and migrations through 15.

## v0.19 — Two-phone boards and explicit Chat → Story curation

`20261012120102_connected_games_and_story.sql` adds private-state Tic-tac-toe/Connect Four RPCs (`velvet_game_create/list/move`) and `velvet_story_save/list/remove`. Privileged functions live in non-exposed `velvet_private`; public wrappers run as caller. New tables use RLS with no client grants. Game row locks plus expected-version checks prevent stale/out-of-turn moves. The pair must contain two verified accounts, anonymous accounts are rejected, and create calls are idempotent with active-round/rate limits.

Story records preserve storage reference, original sender, first saver and caption. An explicit saved reference grants pair-scoped read access to that private object independently of Chat tombstones/hides. Removing the curated record never deletes the Chat message or underlying blob. Save/Delete transactions lock the source message; retries cannot overwrite attribution. The saver owns removal. New security and rules tests run after the original fourteen groups. These are code/CI features; no deployment or physical-phone validation is implied.
