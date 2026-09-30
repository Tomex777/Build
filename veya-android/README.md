# Veya

Veya is a privacy-first Android media downloader prototype built with Jetpack Compose.

## What works

- Paste or share a direct HTTPS video/audio URL into Veya.
- Discover standard OpenGraph, Twitter player stream, and HTML5 `<video>/<audio>/<source>` media exposed by ordinary webpages.
- Probe metadata (content type, size, filename).
- Resumable HTTP downloads using `Range` requests.
- Pause/resume/retry queue backed by WorkManager.
- Foreground progress notification for long downloads.
- Partial downloads remain in private app storage.
- Completed files are published to `Downloads/Veya` through MediaStore.
- Optional Wi-Fi-only constraint.
- No storage, contacts, location, camera, microphone, overlay, usage-access, package-query, or package-install permissions.

## Intentionally not included

This build does not bypass website access controls, DRM, signature protections, or service-specific anti-automation mechanisms. `MediaResolver` is deliberately an interface boundary where authorized source-specific resolvers can be added later.

## Build

Use JDK 17+, Android SDK 36, and a compatible Gradle/AGP toolchain.

## Repository CI

Canonical branch: `veya-android-ci` in `Tomex777/Build`.

Acceptance baseline: compile/target SDK 36, minimum SDK 26. GitHub Actions builds debug and release candidates, then installs the release candidate on API 26 and API 36 emulators and uploads screenshots/runtime reports.
