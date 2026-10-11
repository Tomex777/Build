# completionAction audit (M0.5 prerequisite)

Scope: `ScriptDownloads.kt` as supplied (`DownloadCompletionDispatcher`, `DownloadCompletionWorker`). The download engine
(`AnnieMediaDownloader`, `DownloadStore`, `DownloadItem`) was only available as the older repo snapshot, so everything that depends on it is marked UNVERIFIED.

## Works as designed (from the code read)
- Host-routed wake: completion runs in a WorkManager `CoroutineWorker`, not a JS callback; unique work per download id; the action and payload are persisted in `DownloadItem.completionActionJson`, so a killed process still delivers.
- Failed/cancelled downloads do not fire: the worker returns success without acting unless `state == COMPLETE`; cancelled items are removed, so `find` returns null.
- Payload is capped (32,000 chars) and action name validated by the registry schema (`[A-Za-z][A-Za-z0-9_.:-]{0,127}`).
- Result is appended to the originating chat on channel `download:<id>`; completion fields are cleared afterwards.

## Findings
1. **FIXED IN PATCH (was highest risk): nothing called `DownloadCompletionDispatcher.enqueue`, and the repo's `DownloadItem` had no `completionActionJson`/`completionChatId` fields.** It must be called from the engine when an item becomes COMPLETE (after the file move/publish in `finishFile()`), and from nowhere on fail/cancel. `patches/m0b-repo-files.patch` adds both fields (with persistence in `DownloadStore`) and calls `enqueue` from `AnnieMediaDownloader.finishFile` right after the COMPLETE item is published; failed and cancelled downloads never reach that line. If your local tree already has these, the patch will conflict on those hunks: keep yours and just confirm the enqueue call exists.
2. **FIXED (M0.5): silent loss when the action cannot run.** If `executeAction` returns null (package disabled, action not registered, handler returned nothing) the worker returns success and leaves `completionActionJson` set. It never retries and never tells anyone. Suggest: log a WARN on the owning package, then clear the fields so the item stops looking pending.
3. **FIXED (M0.5): at-least-once, not exactly-once.** (The append is now idempotent per `download:<id>`.) Original finding: The chat message is appended before the fields are cleared. A process death between the two makes WorkManager rerun the action and append a second message. Fix options: make `appendScriptResult` idempotent per `channel` + id, or clear first and accept a rare lost delivery. Pick one deliberately.
4. **FIXED (M0.5): retries were short and silent.** Original finding: Three attempts (`runAttemptCount < 2`), then `Result.failure()` with the item still marked pending. Same remedy as 2.
5. **CHECKED, not a problem: heavy workspace per completion.** `PlatformAndroidCapabilityBackend.close()` only shuts down that instance's own TTS sessions, so a completion worker cannot stop another workspace's speech. Still heavy (reloads every package). Original finding: Each completion builds a full `ScriptWorkspace`, reloads every enabled package, and `close()` calls `androidCapabilities.close()`. Confirm that does not stop another package's TTS or notifications (the backend file was not supplied); if it does, completion and schedule workers need a lightweight close.
6. Disabling a package must stop what it owns (guardrail 5). The worker honours that implicitly (disabled project is not loaded), but nothing cancels the queued WorkManager job on disable. Harmless today because of finding 2's outcome; fix both together.

## L4 test spec (instrumented), from the harness spec item 3
- Start a download with a completion action, kill the app process before it finishes, let it finish, assert the action ran once and a file message was appended.
- Cancel before finish: action never runs. Fail (bad URL): action never runs.
- Disable the package before completion: no message, a WARN is logged, fields cleared.
- `downloads.list()` returns only the caller's items while another package has downloads.
