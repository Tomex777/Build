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

Migration `20261011_000003_velvet_pairing.sql` introduces a verified-account, two-person handshake:

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
