# Cortex for Android

Cortex is the native Android app for operating Night from your phone. The production app targets Android 16 / API 36 and supports Android 8.0 / API 26 and newer.

## Production app

The current app opens directly into the Cortex workspace:

- **Console** — live service status, logs, CPU/RAM/disk/uptime, refresh, start, restart, stop and clear-log controls.
- **Pairing** — multi-account WhatsApp management. Phone-number pairing code is the primary flow; QR opens only when explicitly selected. Expired or invalid sessions use the dedicated re-pair/repair flow.
- **Files** — browse the server project, edit text with undo/redo and find/replace, upload, create, rename, move, duplicate, delete, compress, extract and download.
- **Backups** — create project-only or private backups, restore project backups, delete and export them.
- **Startup** — control startup behavior and install runtime dependencies.
- **Settings** — manage Night modules, commands and supported runtime settings.
- **Activity** — review operational events without exposing secrets.

Cortex shows real connection states and last-known state when appropriate; it does not substitute demo server data.

## Secure connection

Cortex connects only to an HTTPS endpoint. The server address is stored locally and the access token is encrypted with Android Keystore. Cleartext traffic is disabled and remote error bodies are not surfaced to the user.

The server-side Cortex service should remain behind HTTPS. The supplied installer runs the Node service as the dedicated unprivileged `cortex-agent` account, blocks path and symlink escapes from the managed project, excludes protected/server-only paths from normal backups, and limits privileged service-control operations to the installed helper.

## Build and validation

The **Cortex Android** GitHub Actions workflow builds debug and minified release artifacts, validates API 26 and Android 16 / API 36 runtime behavior, captures visual evidence, verifies the minified release APK after process recreation, and publishes ARM64 and x86_64 release APKs plus the AAB and mapping file.

Production signing credentials are intentionally not committed. When the repository release-signing secrets are unavailable, CI signs the installable APK copies with the Android QA/debug key for runtime verification and records that state in `SIGNING.txt`; those QA-signed copies are not the final public signing identity.
