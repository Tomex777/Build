# Annie CI build probe — 2026-10-09

Run the existing GitHub Actions Android build and API 26/API 36 emulator jobs on `annie-android-ci` to establish an executable baseline.

**Important:** This commit is a build probe for the current repository branch. The separately uploaded `annie-m0b-full-merged.zip` is not yet incorporated into this branch; do not label the CI outputs as M0b until the overlay is integrated.

The CI workflow runs Gradle unit tests and debug/release assemblies, Android emulator instrumentation tests, and uploads APK/test artifacts.
