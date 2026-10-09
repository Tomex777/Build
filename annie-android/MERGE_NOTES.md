# Merge notes (read before building)

This is an **overlay**, not a complete checkout. Extract into a full checkout of branch
`annie-android-ci`, then run `python3 annie-android/scripts/repair-m0b-merge.py`.
**Do not apply `patches/m0b-repo-files.patch` directly**: it was cut from an
older snapshot and causes duplicate fields or rejects already-applied changes.
The repaired overlay includes the three new test files from that patch and
the idempotent repair script for its seven existing-file targets.

See `M0B_REPAIR_README.md` for the precise test/build instructions and
Android-SDK requirements.

## Why a patch for some files
The zips you gave me were newer than the Oct 3 `annie-android-ci` snapshot, but they did not contain every file the new work touches. The patch is made against the Oct 3 snapshot for: `AnniePackage.kt`, `AnniePackageArchive.kt`, `ExtensionsManager.kt`, `DownloadManager.kt`, `AnnieMediaDownloader.kt` (plus three new test files). If any hunk fails, your local copy has moved: apply that hunk by hand. Do not overwrite those files wholesale.

## Symbols that exist in no file I was given
`ScriptDownloads.kt` and the patch reference `DOWNLOADS_CAPABILITY`, `DOWNLOADS_START_PERMISSION`, `DOWNLOADS_CONTROL_PERMISSION` (not defined anywhere I can see). Your local tree presumably defines them next to the other `ANDROID_*` constants in `AnniePackage.kt`. If it does not, add them there; the exact string values are yours to choose (they become manifest permission ids).

## Files in this zip that came from your own zips
Everything under `app/src/main` / `app/src/test` that is not listed above: registry, providers, downloads API, collisions, typings, voice files (voice-v2 base). `ScriptWorkspace.kt` is your M0b version plus the service-error, command-collision and typings changes.

## M0.5 additions
New: `ScriptMessages.kt`, `ChatMessageHandleTest` (instrumented). Changed in your files: `ChatHistoryStore.kt` (handles, `changes`, idempotent append), `MainActivity.kt` (`ChatEntry` handle fields, live merge), `ScriptWorkspace.kt` (provider registration, `annie.messages`), `ScriptDownloads.kt` (worker). `operation-ids.lock` gained `messages.send` and `messages.update`; `docs/generated/annie.generated.d.ts` was updated by hand to match the generator, so if the drift test complains, regenerate it with `ANNIE_UPDATE_GENERATED=1` and review.

## Nothing here has been compiled
First run: `./gradlew testDebugUnitTest`, then `scripts/check-annie-types.sh`, then the instrumented tests (`ScriptFilesListTest`, `ServiceErrorTransportInstrumentedTest`).

## Additional Oct 9 integration finding

`AnnieBrowserUi.kt` (in M0b) invokes `AnnieBrowserSessionStore.scrollY(context, spec, instanceId)`
and `saveScroll(context, spec, instanceId, scroll)`, but the Oct 7
`AnnieBrowser.kt` on `annie-android-ci` only has session-level scroll methods.
This is a real Kotlin signature mismatch and leaves different browser bubbles
sharing a scroll key. The revised repair script now updates `AnnieBrowser.kt`
to supply per-instance scroll overloads and cleanup logic. This still needs
instrumented Android testing with multiple open bubbles/tabs.
