# NIGHT Browser — Chromium behavioral references and code-use boundary

Status: engineering reference for **one existing NIGHT Browser candidate** (draft PR #468, `agent/browser-v2-01-coordinator`). This is not a new browser project or an authorization to replace GeckoView with an untested engine.

## Reference sources (verified 2026-10-22)

- Chromium official upstream: https://chromium.googlesource.com/chromium/src/ ; official mirror: https://github.com/chromium/chromium
- Core Chromium open-source license: https://chromium.googlesource.com/chromium/src/+/main/LICENSE (BSD-style; individual third-party source files can have their own notices and licenses).
- Android download dialog states, including `NAME_CONFLICT` and duplicate-dialog suppression: https://chromium.googlesource.com/chromium/src/+/lkgr/chrome/browser/download/download_dialog_types.h
- Android duplicate download prompt and existing/in-progress file text: https://chromium.googlesource.com/chromium/src/+/lkgr/chrome/browser/ui/android/strings/android_chrome_strings.grd
- Code layout and process architecture: https://www.chromium.org/developers/how-tos/getting-around-the-chrome-source-code-directory-structure/

## Decisions applied to NIGHT

1. **Continue GeckoView for the current APK**. Chromium is not a drop-in Kotlin UI dependency. The Chrome browser's tab/navigation/storage/download services are deeply integrated with Blink, Chromium's content layer, native C++ and Android components. Dropping Java files into a Gecko app would introduce broken APIs and two conflicting browsing stacks. Evaluate any engine migration as a separate, measured proposal *after* the existing GeckoView release candidate passes QA.
2. **Reuse behavioral patterns and independently implement Android/Compose equivalents.** Existing NIGHT repeat-download confirmation prompts before another saved file is created and preserves both originals. We also warn when a same-origin, same-named file is currently `PENDING`, `RUNNING`, or `PAUSED`, mirroring Chromium's in-progress duplicate caution. The user can still deliberately start another copy; cancelling never creates a new record or overwrites the original. No Chromium source code has been copied into NIGHT's implementation.
3. **Do not weaken profile isolation.** Private files use SAF without ever reading or writing normal Library history; active/completed warnings are normal-profile only.
4. **Verify actual bytes, not prompts.** Device QA checks original Gecko response file SHA, physically present file, persisted Library entry, repeat cancel, accepted duplicate, media transfer, and offline PDF rendering. The implementation must pass on API 26 and API 36.
5. **Respect ABR.** Native custom inputs, single primary address field, no redundant downloads or navigation controls, `com.night.browser`, minSdk 26, actual installed and visually reviewed APK—not screenshots of a fake interface.
6. **Reuse source only case-by-case.** Before copying any code or assets, record originating commit/path/license, dependency impact and retained notices; for the current branch prefer design-level parity rather than importing the entire Chromium source tree.

## Gates still required before calling the browser finished

- Exact integration SHA: Android Gradle compile, unit tests, ARM64 APK and real installed API26/API36 tests; failures in independent matrix must fail CI.
- Real navigation/history/back-forward, upload from Android SAF with full multipart payload SHA, PNG/PDF/audio/private/authenticated downloads, actual offline PDF pixels, real tabs/thumbnails, user-session restore, private-cookie isolation, Journeys branching, permissions, Peek, Site Studio origin separation, AI consent and real provider output when configured.
- Manual on-device visual review, genuine login/redirect/multipart/media workflows, memory/process death/network loss, physical-phone verification. No merge to `main` or release signing without owner review.
