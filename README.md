# Bailey Host

Bailey Host is a Windows desktop platform for running a WhatsApp bot and independently developed modules on a laptop. Bailey owns the connection, authentication, engine lifecycle, configuration, permissions, jobs, media, storage and desktop UI. Baileys is an engine underneath the host.

```text
WhatsApp
  ↕
Replaceable engine adapter (Lia Baileys or mainstream Baileys)
  ↕
Bailey Host
  ↕ JSONL Protocol 1
Independent module processes
```

Modules do not import either Baileys package and never receive the socket or WhatsApp auth/session directory. Module processes are trusted local programs; Protocol 1 is an integration boundary, not an operating-system sandbox.

## Current platform

- Electron desktop host with system-tray behavior, automatic startup option and a portable Windows build.
- Replaceable WhatsApp engine providers: Lia Baileys is the default and remains supported; mainstream `@whiskeysockets/baileys` is installed and managed separately. Each provider has its own version directory and auth folder. The provider adapter normalizes events and actions for the host.
- Persisted chats, declarative built-in modules, module configuration and automatic settings saving. Secret configuration and cloud credentials use Electron `safeStorage`.
- Bailey Studio visual command editor, code/text editor, module scaffolds, module folder reload, and a tabbed multi-file module workspace.
- JavaScript modules use Bailey's embedded Node and isolated `node_modules`; Python modules can use an isolated `.bailey-venv` when requirements are installed. Other process runtimes can be launched when present on the laptop.
- Protocol 1 commands, passive and richer events, interval/cron/one-time jobs, retry and backoff, persisted scheduler state, lifecycle hooks, module KV/SQLite data, media download/send and proactive messages.
- Module package import/export and Bailey backup/restore. Packages and ordinary backups filter runtime folders, caches, secrets and auth/session material. Backups deliberately omit WhatsApp pairing/session data and engine binaries.
- Provider-neutral storage profiles: local disk, AWS S3 / Cloudflare R2 / Backblaze B2 / MinIO, Azure Blob, Google Cloud Storage and Supabase Storage. Credentials stay encrypted in Bailey Host.
- Host-mediated HTTPS service calls with a per-module domain allowlist and denylist. The `network.http` permission is separate from `network.direct`.
- Network / Data Saver modes: Normal, Metered guidance, Windows Bailey Only, temporary unlocks (5/15/30/60 minutes), emergency disable and a scheduled recovery watchdog.
- Windows CI typechecks, runs unit tests, builds Electron, smoke-launches the host, installs both real engine packages, exercises a packaged module and builds the portable executable.

## Module permissions

Every manifest permission is a request. New modules start with no grants. The user can grant only permissions present in the manifest, and Bailey checks both the request and the stored grant before running privileged host actions.

Common permission names include:

| Permission | Allows |
| --- | --- |
| `whatsapp.send` | Replies and new text sends |
| `whatsapp.react` | Reactions |
| `whatsapp.send-media` | Media sends |
| `media.download` | Downloading recent message media through Bailey |
| `storage.read`, `storage.write`, `storage.delete`, `storage.list`, `storage.link` | Operations on the configured storage profile |
| `kv.read`, `kv.write` | Bailey-managed module key/value data |
| `database.read`, `database.write` | Bailey-managed SQLite data |
| `network.http` | Host-mediated HTTPS requests to domains allowed for that module |
| `network.direct` | Explicitly records that a module asks for direct internet access |

Workers run as child processes and are not OS-sandboxed. The host-mediated HTTP service is default-deny and enforces HTTPS domain policies. Direct networking is not isolated by the OS while Network Lock is Normal; Bailey Only blocks outbound traffic by default at the Windows Firewall and allows Bailey's executable plus DNS and DHCP. Keep modules trusted and use Bailey Only when laptop-wide blocking is required.

## Network / Data Saver

- **Normal** leaves Windows networking unchanged.
- **Metered / Data Saver** avoids Bailey background update checks, leaves system networking unchanged and links to Windows' active-network metered setting.
- **Bailey Only** saves the current Windows Firewall outbound profile actions, adds rules in the `Bailey Host Network Lock` group, then blocks normal outbound traffic from other programs. Bailey itself, DNS and DHCP receive explicit allow rules. Windows prompts for administrator approval only when a firewall change requires it.
- **Temporary Unlock** restores the prior profile state for the selected duration. The watchdog returns to Bailey Only within one minute after expiry if Bailey has stopped. If Bailey stops responding in Bailey Only, it restores the saved Windows state after a 15-minute missed-heartbeat window.
- **Emergency Disable** removes only Bailey-namespaced rules and restores the captured outbound profile actions.

Today’s Network / Data Saver view reports measured storage and host-mediated service payload bytes. WhatsApp protocol traffic, TLS/network overhead, and dependency/update traffic are not currently measured reliably, so Bailey labels them as unmeasured and does not invent a total.

The recovery code and the original firewall profile snapshot are stored with the Windows scheduled task, so the lock can be restored after Bailey crashes or is uninstalled. Deleting the scheduled task manually removes that recovery path.

## Module packages and backups

Module packages include source/config files but exclude `node_modules`, Python virtual environments, `.bailey-runtime`, caches, `.data`, common `.env` files, and auth/session/token files. Reinstall dependencies after import. Bailey backups contain module source/data, chats, commands and configuration; they do not export permissions, the engine install or WhatsApp auth/session files. Secret settings encrypted by Windows may need to be entered again on a different computer.

## Development

Node.js 24 is used by CI. Install dependencies and run the local checks:

```bash
npm install --no-audit --no-fund
npm run typecheck
npm test
npm run build
npm run smoke
```

The Windows workflow additionally runs:

```bash
npm run engine:smoke
npm run engine:baileys-smoke
npm run dist:win
```

Real-account pairing and physical-phone testing are a later validation phase. CI installs engine packages but does not connect to WhatsApp or change a runner’s firewall.

## Protocol and examples

- [Bailey Module Protocol 1](docs/MODULE_PROTOCOL.md)
- [Python example module](examples/python-module)
- [JavaScript example module](examples/javascript-module)
