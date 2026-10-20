# Agent 6 — Library / download integration handoff (2026-10-20)

**Branch:** agent/browser-06-library
**Scope:** New LibraryRepository.kt, DownloadLibrary.kt, NightLibraryScreen.kt, NightLibraryRulesTest.kt.
**Task:** B-006.

## Implemented on isolated branch
- Searchable Bookmarks with folder labels, title/folder editing, deletion and persistence, while showing unmigrated JourneyStore bookmarks without losing them.
- Searchable local visit history (read-only, from actual Journey nodes).
- Saved reading list with add/remove/open.
- DownloadManager-driven HTTP(S) downloads with persisted IDs, actual status and byte counts, failure reasons, failed retry, cancel/delete, open through provider URI, and SAF export.
- Direct-download link form for functional entry without waiting for Gecko hooks.
- Filename traversal sanitization and pure unit tests. No pretend pause/resume feature.

## Required coordinator integration (shared files NOT edited)
1. Agent 1 / Agent 2: Replace the existing private LibraryPanel(browser, onOpen) invocation in MainActivity's Page.LIBRARY branch with NightLibraryScreen(browser, onOpen). Delete obsolete private LibraryPanel only after reviewing Agent 2's Home/toolbar edits; this branch deliberately does NOT edit MainActivity.
2. Agent 4: Gecko ContentDelegate.onExternalResponse supplies WebResponse including a live InputStream body. IMPORTANT: this stream must be consumed/closed properly, not discarded or retried with DownloadManager without validation: a second request can fail authenticated or single-use downloads. Add a stream-to-file adapter and report real bytes/failures; do not silently use DownloadManager to fetch such streams again. PR #427 exposes downloadHandler, but it needs privacy and stream-handling coordination.
3. Agent 3: private tabs must never feed URL/history into JourneyStore and must NOT silently enqueue Android's system DownloadManager (global system-visible history). Explicit save/export consent is required for a private-session file.
4. Agent 5: book/history source-of-truth sync and deletion must be coordinated. Current Legacy JourneyStore bookmark rows are read as fallback. Removing one in the new Library hides it there; the legacy Home shortcut may remain until the coordinator adopts one canonical bookmark service.
5. Agent 4 / QA: Android 8/9 system DownloadManager destination-permission behavior must be verified on device; do not claim API 26 file handling tested until it is actually exercised.

## Reality-based verification status
- Code written: YES; branch commits visible on GitHub.
- Android Gradle build: NOT RUN (this execution environment has no Android SDK/Gradle runtime).
- APK installation: NOT RUN.
- Android interaction: NOT RUN.
- MIME scenarios, authenticated Gecko downloads, failure/retry, SAF export, rotation: NEED TO TEST.
- Integrated into Home/Gecko controller: NOT YET.

## Manual QA scripts
1. Save a current page to Bookmarks; edit title and assign a folder; search title/folder; reopen after process death; delete.
2. Open 5 sites and inspect recent History entries, inspect title/time, search and reopen.
3. Save/remove Reading list entries and restart.
4. Download a direct public PDF, PNG/JPEG and a wrong/missing URL; verify actual download bytes and state, open in external viewer, export with SAF, delete. Induce network interruption: Android-paused or failed status must reflect provider.
5. Re-run on API 26 and API 36, including the private-profile scenario after Agent 3 merges.
6. For Gecko-triggered files test logged-in downloads and single-use signed URLs specifically; do not mark complete until response body streams are safely handled.

Integration PR targets feature/night-browser-multi-agent-20261020; never main.
