# Annie cloud-build baseline — 2026-10-09

This commit triggers the existing Annie Android CI workflow on `annie-android-ci`, including Gradle unit tests, APK/AAB assembly, Android API 36 emulator UI tests, and Android API 26 acceptance tests.

## Verification scope

**Important:** The repository branch does not yet contain the user-supplied `annie-m0b-repaired-2026-10-09-v2.zip` overlay. Therefore CI results from this commit verify only the current checked-in branch, not the newer M0b/M0.5 files. Do not label artifacts as validated M0b unless the overlay is merged first.

After integration, CI must compile the newer Kotlin/Compose files, execute `:app:testDebugUnitTest`, build installable APKs, test on API 26 and API 36 emulators, and upload test reports and screenshots.