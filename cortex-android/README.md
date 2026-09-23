# Cortex for Android

Cortex is the phone control center for Night. The app targets Android 36 and supports Android 8.0 (API 26) and newer.

## Phone project files

Use **Library** to create files or import a project ZIP. Files live in the app's private storage on the phone. Text files can be edited in Cortex. The phone workspace deliberately skips `node_modules`, Git and Gradle caches, `.env*` files, private keys and keystores. API tokens and the Blob SAS URL are encrypted with Android Keystore.

There is no background upload or download. **Sync backup** sends only files whose SHA-256 differs from the manifest in Azure Blob. Cortex writes a small manifest when project files change. **Restore from Blob** downloads changed files only after confirmation and stops if it detects local edits that are not in the backup. Removing a phone copy does not remove its Blob copy.

For Blob backup, enter an HTTPS container SAS URL with read, write and create permissions. Cortex stores objects under the `Night/` prefix in that container. Treat the SAS URL like a password; use a short expiration and restrict it to a dedicated container.

## Deploy to Azure

Choose Azure under **Settings → Hosting**, enter the HTTPS Cortex Agent URL and agent token, then use **Deploy to Azure** in Library. Cortex uploads changed workspace files to the configured Night project root through the agent. If `package.json` or `package-lock.json` changes, the VM runs `npm ci --omit=dev` (or `npm install --omit=dev` when no lockfile exists), then Cortex restarts Night. `node_modules` stays on the VM.

The agent is not exposed on the public internet by the installer. Put it behind HTTPS and an authenticated reverse proxy before connecting the phone. See [`../cortex-agent/README.md`](../cortex-agent/README.md) for VM setup.

## Build and checks

The **Cortex Android** GitHub Actions workflow builds a debug APK and runs a UI smoke test in an API 36 Android emulator. It publishes the APK and emulator screenshot as workflow artifacts.
