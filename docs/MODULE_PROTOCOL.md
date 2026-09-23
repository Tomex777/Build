# Bailey Module Protocol 1

Protocol 1 connects Bailey Host to independently developed module processes using newline-delimited JSON (JSONL) on stdin/stdout. Bailey owns the WhatsApp connection, engine selection, pairing/session data and all host services. Modules never receive the WhatsApp socket or auth/session directory, and must not import Lia Baileys or mainstream Baileys.

Modules may use JavaScript, Python, Java, Go, Rust or another executable runtime. Bailey launches the configured process without a shell. JavaScript can use Bailey's embedded Node runtime; Python can use a per-module `.bailey-venv`.

## Module folder and manifest

Each module lives in its own folder under Bailey Host's Modules directory:

```text
modules/
└── economy/
    ├── bailey.module.json
    ├── main.py
    ├── requirements.txt
    └── package.json       # JavaScript modules
```

Studio can create a starter module, browse/edit files in multiple tabs, install dependencies, reload modules and import/export `.baileypkg` packages.

```json
{
  "protocol": 1,
  "id": "economy",
  "name": "Economy",
  "version": "1.0.0",
  "description": "Economy commands and storage.",
  "runtime": { "command": "python", "args": ["main.py"], "restart": "on-failure" },
  "capabilities": ["commands", "settings", "events", "jobs", "storage", "services", "lifecycle"],
  "permissions": ["whatsapp.send", "whatsapp.react", "kv.read", "kv.write", "database.read", "database.write"],
  "events": ["message.received", "reaction.received", "group.participant", "call.received"],
  "commands": [
    { "id": "balance", "name": "balance", "section": "Economy", "description": "Show your balance." }
  ],
  "jobs": [
    { "id": "interest", "cron": "0 0 * * *", "retry": { "maxAttempts": 3, "backoffSeconds": 10 } }
  ]
}
```

`capabilities` opt into Protocol 1 features; `permissions` ask Bailey for specific authority. A manifest permission does not grant itself. Users grant or revoke only manifest-requested entries under Studio → Module runtimes & health. A privileged action requires both a matching request and a saved user grant. Missing permission storage means no grants.

Permission matching supports exact entries and parent wildcards, such as `storage.*`. The UI grants a requested wildcard as a single entry. Current privileged actions include:

| Action/service | Permission |
| --- | --- |
| Reply or send text | `whatsapp.send` |
| React | `whatsapp.react` |
| Send media | `whatsapp.send-media` |
| `media.download` | `media.download` |
| `storage.info`, `storage.get`, `storage.exists` | `storage.read` |
| `storage.put`, `storage.delete`, `storage.list` | `storage.write`, `storage.delete`, `storage.list` respectively |
| `storage.temporary-link` | `storage.link` |
| `kv.get` / `kv.list` | `kv.read` |
| `kv.set` / `kv.delete` | `kv.write` |
| `database.all` / `database.get` | `database.read` |
| `database.run` / `database.exec` | `database.write` |
| `network.request` | `network.http` |
| `host.info` | `host.info` |
| Proactive `send` action | `whatsapp.send` |

Permission grants control Bailey's host-mediated operations. Module workers are ordinary trusted local processes rather than OS sandboxes. Use Bailey Only to block direct outbound connections at the Windows Firewall; in Normal mode, a worker that uses its runtime's raw networking APIs is not OS-isolated.

### Runtime choices and dependencies

For Python:

```json
{ "command": "python", "args": ["main.py"] }
```

Python must be installed on the computer. Add packages to `requirements.txt`; Studio installs them into that module's `.bailey-venv` and restarts a running worker after a successful install.

For JavaScript:

```json
{ "command": "bailey-node", "args": ["main.mjs"] }
```

`bailey-node` uses Node embedded in Bailey Host. Add dependencies to the module's `package.json`; Studio installs them into that module's own `node_modules` with Bailey's bundled npm/Node, then restarts the worker after a successful install. Other runtimes can name an installed executable directly, such as `java`, or ship a compiled executable in the module folder.

Settings declared in the manifest automatically appear under Configuration. ENV-backed settings are injected when a worker starts. Secret settings and cloud credentials stay in Bailey's encrypted host configuration.

## Request and response envelope

Bailey writes one JSON object per line. A module response references the request `id` in `replyTo`:

```json
{"protocol":1,"replyTo":"request-1","ok":true,"actions":[]}
```

Failures use `ok:false` and an error string:

```json
{"protocol":1,"replyTo":"request-1","ok":false,"error":"Database unavailable"}
```

Keep stdout for protocol JSONL only. Use stderr or a `log` action for diagnostics. Bailey waits up to 30 seconds for a command/event/job response and 5 seconds for lifecycle start/stop.

## Commands and actions

For a command, Bailey sends:

```json
{"protocol":1,"id":"request-1","type":"command.execute","commandId":"balance","context":{"remoteJid":"...","senderJid":"...","text":".balance","args":[]}}
```

`context` contains the triggering JID and parsed arguments. Return the action sequence as one JSON line:

```json
{"protocol":1,"replyTo":"request-1","ok":true,"actions":[{"type":"reply","text":"Balance: ₦500"},{"type":"react","emoji":"✅"}]}
```

Supported actions are `reply`, `react`, proactive `send`, `send-media`, and `log`. Reply/send actions require `whatsapp.send`; reaction requires `whatsapp.react`; media sends require `whatsapp.send-media`. Bailey applies actions through its current engine adapter.

## Events

Declare the `events` capability and optionally choose subscriptions. If `events` is enabled and `events` is omitted, the default subscription is `message.received`.

```json
{"protocol":1,"id":"request-2","type":"event.dispatch","event":"message.received","context":{"id":"ABCD","remoteJid":"...","senderJid":"...","text":"hello","pushName":"Ada","timestamp":1789674000000}}
```

Protocol 1 event names currently include:

- `message.received`
- `message.updated`
- `reaction.received`
- `group.participant`
- `call.received`

Engine support determines which richer event payloads are available. Events are opt-in; the bot's own messages are not sent as `message.received`. A message may be dispatched to commands and passive events independently. Respond with an empty `actions` array to ignore an event. Disabled modules receive no event dispatch.

For incoming media, the `message.received` context can include media metadata and a message `id`. A module with the `media` capability, `storage` capability and `media.download` grant may request the downloaded payload through Bailey's media service. Modules receive a path under their data directory, never the WhatsApp socket.

## Scheduled jobs and lifecycle

Declare the `jobs` capability and define exactly one schedule per job: interval, five-field cron, or one-time `runAt` timestamp in milliseconds.

```json
{"id":"hourly-report","intervalSeconds":3600,"runOnStart":false,"retry":{"maxAttempts":3,"backoffSeconds":10}}
```

Bailey owns timers and persists next-run and completion state across host restarts. Intervals must be 5 seconds to 30 days; retry attempts are 1–5. Disabled modules do not execute jobs. Bailey prevents overlapping runs of the same module/job pair. Reloading stops the old scheduler before creating the new one.

Job request:

```json
{"protocol":1,"id":"request-3","type":"job.execute","jobId":"hourly-report","scheduledAt":1789674000000}
```

A job has no triggering chat, so it cannot use `reply` or `react`. It may return a permitted `send` action for a proactive notification or return log actions.

With the `lifecycle` capability, Bailey sends `lifecycle.start` before the module's first regular request and `lifecycle.stop` during an intentional worker shutdown/reload. Return an ordinary empty success response when no work is needed. Unexpected worker exits are reported in diagnostics and restarted according to `runtime.restart`.

## Persistent data and shared services

With the `storage` capability, the worker receives `BAILEY_MODULE_DATA_DIR`. This per-module data directory is separate from source, engine files and WhatsApp auth. It survives worker restarts/reloads and is included in Bailey backups. Use it for module-owned state and media results.

Bailey registers these shared services:

| Service | Methods | Purpose |
| --- | --- | --- |
| `host` | `info` | Protocol/module metadata |
| `kv` | `get`, `set`, `delete`, `list` | Small JSON values persisted per module |
| `database` | `all`, `get`, `run`, `exec` | SQLite database in the module data directory |
| `storage` | `info`, `put`, `get`, `delete`, `exists`, `list`, `temporary-link` | Provider-neutral object storage |
| `media` | `download` | Download recent incoming media into module data |
| `network` | `request` | Allowlisted host-mediated HTTPS request |

A module must declare `services` before it may send `host.call`. Example request:

```json
{"protocol":1,"id":"call-1","type":"host.call","service":"kv","method":"get","params":{"key":"balance:123"}}
```

Bailey replies on stdin, without completing the original request:

```json
{"protocol":1,"type":"host.result","replyTo":"call-1","ok":true,"result":{"key":"balance:123","found":true,"value":500}}
```

Each host call has its own id. A worker may make service calls while a command/event/job request is pending. It must continue reading stdin, match `host.result.replyTo`, and then answer the original request. Service failures return `ok:false` with an error. `services` capability alone is not authorization; each method has its own permission. Storage provider credentials never appear in manifests, host results or module environment variables.

The `network.request` service accepts `url`, optional `method`, `headers`, `text` or `base64`, and optional `responseEncoding:"base64"`. It accepts HTTPS public-domain URLs only, caps request bodies at 256 KB and response bodies at 2 MB, and times out at 15 seconds. The module must request and receive `network.http`, and its hostname must match the module's allowlist in Settings → Module network access. Deny rules override allow rules; an empty allowlist denies all host-mediated network requests. Bailey does not forward redirects automatically.

When Bailey Only is active, Python dependency installation creates a temporary outbound allow rule for that module's virtual-environment Python executable, then removes the rule when pip exits. Engine installs, JavaScript dependency installs and host-mediated requests use Bailey Host's already allowed executable. The remaining laptop stays locked during these operations.

## Storage profiles

The user creates named/default storage profiles in Configuration. Protocol 1 modules select `profile` by name or omit it to use the default. Providers include local disk, S3-compatible endpoints, Azure Blob, GCS and Supabase Storage. Storage is permission-gated by operation. Profile credentials remain encrypted in Bailey Host. Bailey tracks known storage payload byte counts; it does not report TLS overhead or pretend to measure WhatsApp protocol traffic.

## Package and backup behavior

Module exports and imports use `.baileypkg`. Bailey excludes `node_modules`, `.venv`/`.bailey-venv`, `.bailey-runtime`, caches, `.data`, common `.env` files, and common auth/session/token file and folder names. Reinstall runtime dependencies after import.

Ordinary `.baileybackup` files include local host configuration, chats, commands, module source/data and storage objects. They deliberately exclude WhatsApp auth/session directories and installed engine binaries, and reject unsupported/sensitive paths on restore. Permission grants are user authority and are not included in backup import/export. Settings protected by OS `safeStorage` may need to be re-entered on another computer.

## Runtime and trust notes

- `bailey-node` uses embedded Node; Python and other executable runtimes are local installations.
- Workers may use only Bailey-managed interfaces for guaranteed host policy checks. A worker is not sandboxed from the local filesystem or raw sockets while Windows is in Normal mode.
- Network Lock Bailey Only changes Windows Firewall outbound defaults and is Windows-specific. It adds only namespaced rules and uses an elevated scheduled watchdog to restore captured profile values after Bailey stops heartbeating.
- The first real-account/phone test is a separate phase after CI validation.
