# Torri 0.21.2 — safe upgrade and verification

This checkpoint incorporates the Mihon v0.21.0–0.21.2 reader, library, download,
database, extension-security and Cloudflare improvements into **Torri by Night**.
Torri retains its `app.torri` package, Night theme, compatible extensions and
existing reading experience.

## Critical: back up before migrating

Mihon 0.21.x merges duplicate manga and chapter records left by older releases.
That library change **cannot be undone** simply by installing an older APK.

1. In your **existing Torri installation**, open Settings → Data and storage →
   Create backup. Select all desired categories, history, chapters and tracking
   data as offered by the version you are using.
2. Save the backup **outside the application's private storage** (e.g., a
   user-accessible Documents location). Copy it elsewhere if possible.
3. Confirm that the backup file exists and can be reopened through Android's
   file picker. Retain a separate copy before any migration.
4. If you rely on old `.tachibk` files or Tachiyomi 1.4-format extensions, note
   that Mihon 0.21.2 is the **last upstream major version** supporting them.
   Migrate old backup formats while this release remains available.
5. Do **not** uninstall the existing app to bypass a signature mismatch.
   Uninstalling might delete reading data and downloads.

## Choosing the correct APK

| Candidate | Android package | Installation purpose |
|---|---|---|
| Torri production | `app.torri` | In-place upgrades only when the signing certificate matches the installed Torri |
| **Torri Preview RC2** | `app.torri.rc` | Side-by-side clean-install evaluation, does **not** migrate the installed Torri library |
| Torri debug | `app.torri.dev` | Internal Android developer/emulator testing |

The CI-built APKs are signed by temporary runner-generated signing keys unless a
stable Torri signing key is configured. A temporary key cannot safely upgrade a
previous production install. The **Preview RC2** uses a deliberately different
package ID to protect the user's existing library.

The release candidate is test-only; a passing APK compilation is not proof that
all extensions or Cloudflare-protected sources work on every phone.

## Checks before a production release

- [x] Verify source formatting with `./gradlew spotlessCheck` — passed for the initial 0.21.2 integration.
- [x] Run `./gradlew testDebugUnitTest` — passed for the initial integration.
- [x] Verify SQLDelight migrations with `./gradlew verifySqlDelightMigration` — passed for the initial integration.
- [x] Compile `./gradlew assembleRelease` — passed for the initial integration.
- [ ] Revalidate checks after Torri-specific fixes and the RC build-type addition.
- [ ] Verify the Torri Preview APK's package/version/signature and that it installs separately.
- [ ] Boot the app on an Android emulator and confirm home/resume works.
- [ ] Smoke-test long-strip and paged readers, loading the next chapter, offline downloads,
      history, large libraries, installed sources, extension upgrades and backup restore.
- [ ] Confirm a signed in-place upgrade using the production signing certificate, on
      a **disposable test install with a tested backup**, before releasing to real users.
- [ ] Keep PR #4 in draft until the remaining checks pass.

## Known CI limitations

This repo hit GitHub Actions artifact storage quota. A green workflow job can
therefore have unsuccessful artifact uploads; APK artifacts should not be claimed
as available unless the corresponding GitHub Release or other distribution
location actually contains verified files.

## Links

- Torri PR: https://github.com/Tomex777/Torri/pull/4
- Upstream Mihon 0.21.2: https://github.com/mihonapp/mihon/releases/tag/v0.21.2
