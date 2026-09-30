# Cubic

Cubic is a native Android 3D twisty-puzzle playground and teaching app.

## v1 scope

Cubic v1 supports generated puzzles from 2 to 9 layers on each axis, including cubes and cuboids such as 3×3×5 and 2×4×6. It provides physical-looking OpenGL cubies, orbit and pinch camera controls, face and inner-layer turns, scrambling, undo/reset, solved-state detection, and guided teaching.

The v1 guided solver is deliberately scoped to puzzle states created through legal moves inside Cubic. It uses the recorded move history to produce a guaranteed valid route back to solved and teaches that route one move at a time in the 3D viewport.

Cubic v1 does **not** claim to solve an arbitrary external real-world cube state. Manual sticker entry, physical-state validation, and a genuine arbitrary 3×3 solver are deferred to a later milestone rather than being faked.

## Android

- compileSdk 36
- targetSdk 36
- minSdk 26
- versionName 1.0.0
- versionCode 1

## Release signing

CI produces an installable release candidate using Android debug signing only for emulator/device QA.

Permanent public distribution must use the stable owner signing key via GitHub Actions secrets. No private keystore or password belongs in the repository. The release workflow expects these secrets:

- CUBIC_RELEASE_KEYSTORE_B64
- CUBIC_RELEASE_KEY_ALIAS
- CUBIC_RELEASE_KEYSTORE_PASSWORD
- CUBIC_RELEASE_KEY_PASSWORD

The same production key must be preserved for all future Cubic updates.


## Production release workflow

The normal Cubic CI workflow builds and tests a QA release candidate. It does not pretend that a debug-signed APK is the public release.

For the first public release, configure these repository Actions secrets with the permanent owner signing identity:

- `CUBIC_RELEASE_KEYSTORE_B64`
- `CUBIC_RELEASE_KEY_ALIAS`
- `CUBIC_RELEASE_KEYSTORE_PASSWORD`
- `CUBIC_RELEASE_KEY_PASSWORD`

After the validated commit is ready, create a release tag such as `cubic-v1.0.0` pointing at that commit. The tag push runs **Cubic Production Release** from the tagged revision. Normal Cubic CI also uses the owner key automatically when all four secrets are present. Manual `workflow_dispatch` becomes available once this workflow is also present on the repository default branch.

That workflow:

1. requires all signing secrets,
2. runs unit tests and release lint,
3. builds the unsigned 1.0.0 APK,
4. signs it with the permanent owner key,
5. verifies package/version/minSdk/targetSdk,
6. verifies arm64-v8a, armeabi-v7a, x86, and x86_64 coverage,
7. installs and exercises the exact production-signed APK on API 26 and API 36,
8. runs the visible-3D, bottom-sheet, size, face/layer, scramble, undo/reset and guided-solve proofs, and
9. publishes `Cubic-universal-release.apk` plus signature, checksum, runtime reports and screenshots.

Keep the same production signing key for every future Cubic update.


The final public-named artifact, `Cubic-universal-release`, is published only after both API 26 and API 36 runtime jobs succeed. Signing evidence may be staged earlier inside the workflow, but it is not labeled as the final release until runtime validation passes.


## Permanent v1 signing identity

The first permanent Cubic Android signing identity was created once and validated by **Cubic Signing Bootstrap #1**.

Public certificate fingerprint:

`SHA-256: CFD16302F35EA53892D2B319D6A52A0D0E9A53A5310B0CE20EFD99773A9F4CBE`

This identity must be reused for every future direct-APK Cubic update. Never generate a replacement key for an update to the same package.

The private backup from bootstrap run #1 contains the keystore and the four GitHub Actions secret values. Store that backup securely offline and configure these repository secrets before future production builds:

- `CUBIC_RELEASE_KEYSTORE_B64`
- `CUBIC_RELEASE_KEY_ALIAS`
- `CUBIC_RELEASE_KEYSTORE_PASSWORD`
- `CUBIC_RELEASE_KEY_PASSWORD`

The one-time bootstrap workflow is intentionally removed after successful creation so a later push cannot silently generate a different signing identity.
