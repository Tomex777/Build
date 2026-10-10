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
