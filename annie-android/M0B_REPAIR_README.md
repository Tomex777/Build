# Annie M0b/M0.5 repair overlay — 2026-10-09

**This ZIP is not a complete Android project.** It contains only changed files.
To assemble it, start from the full `Tomex777/Build` repository, branch
`annie-android-ci`, and overlay the `annie-android/` directory from this ZIP.
Do **not** run `patches/m0b-repo-files.patch` directly: it is based on an older
snapshot, and its fuzz application duplicates manifest fields.

The repair script is an idempotent, fail-closed replacement for that patch.
It applies the missing pieces to the full checkout:

- removes duplicate `AnniePackageManifest` `requires/publisher/networkHosts`;
- rewires archive decoding/encoding to `ApiCompatibility` and `ManifestIdentity`;
- checks or restores download completion fields and persistence;
- dispatches completion actions only after a file is marked COMPLETE;
- adds command-collision and network-reach rows to the Extensions UI;
- fixes selected-text deletion in Script Studio's Sora editor;
- adds per-instance scroll persistence to the older `AnnieBrowserSessionStore`,
  which the newer `AnnieBrowserUi.kt` already calls with four/three parameters.
  Without these overloads, the merged app has a Kotlin compile error.
- adds strict/legacy dialect mode to the operation registry, propagated from
  `ScriptWorkspace` (legacy packages ignore extra fields; required fields and
  permissions remain enforced for both dialects).

The repair script refuses to modify **any** file when an unexpected revision is
found. It creates no new remote branch and touches only the six named existing
files in the working copy; the other changes come from the ZIP overlay itself.

## Apply and build on a machine with Android SDK

```bash
git clone -b annie-android-ci https://github.com/Tomex777/Build.git
# Extract this repaired ZIP at the checkout ROOT, allowing replacement:
unzip -o annie-m0b-repaired-overlay.zip -d Build
cd Build/annie-android
python3 scripts/test-repair-contract.py
python3 scripts/repair-m0b-merge.py --check
python3 scripts/repair-m0b-merge.py
# The old patch ALSO adds these three new test files. They are bundled here
# separately; the other hunks are covered by the repair script.
./gradlew testDebugUnitTest
sh scripts/check-annie-types.sh
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

For hardware verification, run `./gradlew connectedDebugAndroidTest` on an
emulator/device. This execution environment cannot run Android/Compose Gradle tasks
because no Android SDK/Gradle distribution is available. Thus no APK is included
in this ZIP, and Android-dependent code changes are **unverified**, not passing.

## Outstanding P0 checks

- Browser WebViews appear keyed to a unique `instanceId` in the ZIP, but must be
  tested with two simultaneous bubbles/tabs on an Android device, including
  scroll, navigation, cookies, and lifecycle.
- YouTube wrong-video-play bug needs reproduction on device and source-file
  tracing; it is **not claimed fixed** in this overlay.
- Script Studio selection/deletion path is patched using Sora's `text.replace`;
  validate Select All > Backspace and Select All > Delete on physical keyboards.

The message bubble system, new Android bridge domains, and per-package AI bridge
remain deferred until P0 + build verification are closed.

## Offline checks run on 2026-10-09

- JDK 21 and system Kotlin 1.9 compiled and ran the core compatibility/canonicalization smoke check.
- JDK 21 and Kotlin 1.9 compiled the operation-registry smoke check with a **test-only** `org.json` shim.
- The actual `CoreAndroidOperationProvider`, downloads, and messages operation **declarations** were compiled with the JVM Kotlin compiler and test-only Android/JSON stubs. Registry-generated TypeScript was compared byte-for-byte to `docs/generated/annie.generated.d.ts`: **19 operations, 7,603 characters, no drift**.
- `scripts/check-annie-types.sh`: TypeScript sample compilation **passed**.
- `scripts/test-repair-contract.py`: repair transforms of seven files are idempotent on fixtures and reject unrecognized archive layouts. These are **not tests of the actual GitHub checkout**.

These checks do **not** imply Gradle, Android SDK or physical-device success.
A full APK cannot be produced from this overlay alone without the complete
GitHub checkout and an Android SDK/Gradle toolchain. Do not distribute an APK
as verified until `assembleDebug` and relevant connected Android tests pass.
