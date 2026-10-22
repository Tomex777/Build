# Wave-2 A6 updates — 20 October 2026

**Source head:** `76c21aba6fc15b6cff622d2ce9501d743ca92d51` (at note time), existing specialist PR #432. A1 should reuse this source instead of creating a duplicate disconnected Library. Integration still missing; original first-wave notes below describe the earlier state.

- **Signed/authenticated download privacy:** the Android system service receives the original request; NIGHT's own SharedPreferences now retain a query-free/credential-free URL only. Legacy saved URLs are re-serialized at startup to remove tokens. Retry is available only for failed simple public HTTP(S) URLs with no user-agent/cookie headers; signed, ephemeral and authenticated streams must restart from their original webpage. Unit cases cover this guard.
- **True private download export:** Gecko original response bytes go directly to an Android Storage Access Framework `CreateDocument` URI and **never** to persistent Library, app-private download folder or Android DownloadManager. Declining/private picker cancellation closes the stream; errors attempt deletion of incomplete documents. This is only active after tab privacy is bound; cannot infer privacy from a URL.
- **Mandatory combined integration contract:** with A4's `downloadHandler: ((BrowserTab, WebResponse) -> Unit)?` and A3's `BrowserTab.isPrivate`, wire `browser.downloadHandler = { tab, response -> consent.offer(response, tab.isPrivate) }`. Plain `consent.offer(response)` retains public behavior for legacy callers and is **not adequate for private tabs**. Render `NightDownloadPrompt(consent)` and `NightLibraryScreen(browser, onOpen)` in coordinator-owned MainActivity. Keep the `browser-downloads/` FileProvider path.
- **Filename quality:** RFC 5987 filenames preserve literal `+`, support UTF-8/ISO-8859-1 and remove Unicode bidirectional filename-spoofing characters. Regression tests included.
- **Verification:** Earlier head `7a030f7` had Android/Agent6 CI success (runs 39378081988 / 39378081967). Later commits above triggered **new workflows still queued at documentation time**. Neither compile success on new head nor API26/API36 install/authenticated live-site tests should be inferred. A10 should specifically run incognito response → SAF picker → bytes verified → Library empty, signed URL → persisted prefs redacted, aborted partial file, and private-cancel cleanup.

---

# Agent 6 — Library / download integration handoff (2026-10-20)

**Branch:** agent/browser-06-library
**Scope:** LibraryRepository.kt, DownloadLibrary.kt, GeckoDownloadNames.kt, NightDownloadPrompt.kt, NightLibraryScreen.kt, NightLibraryRulesTest.kt, FileProvider XML, isolated CI workflow.
**Task:** B-006.

## Implemented on isolated branch
- Searchable Bookmarks with folder labels, title/folder editing, deletion and persistence, while showing unmigrated JourneyStore bookmarks without losing them.
- Searchable local visit history (read-only, from actual Journey nodes).
- Saved reading list with add/remove/open.
- DownloadManager-driven HTTP(S) downloads with persisted IDs, actual status and byte counts, failure reasons, failed retry, cancel/delete, open through provider URI, and SAF export.
- Direct-download link form for functional entry without waiting for Gecko hooks.
- Gecko `WebResponse.body` ingestion on background worker from the **original response stream**, with prompt confirmation, real bytes, cancellation, process-restart interruption status, app-private files, external-open via FileProvider and SAF export. No second URL request. `Content-Disposition` filename handling and URL token redaction.
- Own CI workflow triggered by PR/push to compile and test this branch (do not assume passing until GH Actions concludes).
- Filename traversal sanitization and pure unit tests. No pretend pause/resume feature.

## Required coordinator integration (shared files NOT edited)
1. Agent 1 / Agent 2: Replace existing `Page.LIBRARY -> LibraryPanel(browser, onOpen)` in MainActivity with `Page.LIBRARY -> NightLibraryScreen(browser, onOpen)`. Delete obsolete private `LibraryPanel` only after reviewing Agent 2's Home/toolbar edits; this branch does NOT edit MainActivity.
2. Agent 4 / Agent 1: `val downloadConsent = WebDownloadConsent(DownloadLibrary.shared(applicationContext))` in Activity after browser init, `browser.downloadHandler = downloadConsent::offer` on Agent 4's `downloadHandler` hook, and render `NightDownloadPrompt(downloadConsent)` inside the `setContent` tree (after main browser UI). Consent declines close the stream. Consent approvals call `acceptGeckoResponse(response)` which copies the original response to app-private storage; no URL refetch.
   This requires Agent 4's PR #427 to be integrated first, or equivalent hook on the combined browser controller.
   The FileProvider `res/xml/file_paths.xml` adds the app-private `browser-downloads/` path; preserve it when Agent 7 merges.
3. Agent 3: Private tab downloads must require the same explicit consent. Gecko originals are stored in the app (not DownloadManager), but once the user accepts, they will show in regular Library downloads; user MUST be aware. For stronger incognito guarantees route accepted private downloads only to SAF and never to persistent Library.
4. Agent 5: book/history source-of-truth sync and deletion must be coordinated. Current Legacy JourneyStore bookmark rows are read as fallback. Removing one in the new Library hides it there; the legacy Home shortcut may remain until the coordinator adopts one canonical bookmark service.
5. Agent 4 / QA: Android 8/9 system DownloadManager destination-permission behavior must be verified on device; also test Gecko authenticated POST download, Content-Disposition, kill process mid-transfer, FileProvider open, SAF export, and decline/cancel. Do not claim API 26 file handling tested until exercised.

## Reality-based verification status
- Code written: YES; branch commits visible on GitHub.
- Android Gradle build: CI queued, **not yet confirmed** (this execution environment has no Android SDK/Gradle runtime).
- APK installation: NOT RUN.
- Android interaction: NOT RUN.
- MIME scenarios, authenticated Gecko downloads, failure/retry, SAF export, rotation: NEED TO TEST.
- Integrated into Home/Gecko controller: NOT YET; requires coordinator-owned shared MainActivity and Agent 4 callback wiring.

## Manual QA scripts
1. Save a current page to Bookmarks; edit title and assign a folder; search title/folder; reopen after process death; delete.
2. Open 5 sites and inspect recent History entries, inspect title/time, search and reopen.
3. Save/remove Reading list entries and restart.
4. Download a direct public PDF, PNG/JPEG and a wrong/missing URL; verify actual download bytes and state, open in external viewer, export with SAF, delete. Induce network interruption: Android-paused or failed status must reflect provider.
5. Re-run on API 26 and API 36, including the private-profile scenario after Agent 3 merges.
6. For Gecko-triggered files test logged-in downloads, POST and single-use signed URLs with the consent prompt and bytes stream; verify no second request, correct file bytes and closure on denial.

Integration PR targets feature/night-browser-multi-agent-20261020; never main.
