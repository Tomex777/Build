# Bailey Module Protocol 1

Bailey Host owns the WhatsApp connection. Code-backed modules run as separate processes and communicate with Bailey over newline-delimited JSON (JSONL) on stdin/stdout.

This keeps modules independent of Lia Baileys internals and allows modules to be written in Python, JavaScript, Java, Go, Rust, or any runtime that can read and write JSON lines.

## Module folder

Each module lives in its own directory under Bailey Host's Modules folder:

```text
modules/
└── economy/
    ├── bailey.module.json
    ├── main.py
    └── ...
```

Bailey Host → Studio → **Open modules folder** opens this location. Studio can also create a starter module for you, including its manifest, worker file and README.

## Manifest

```json
{
  "protocol": 1,
  "id": "economy",
  "name": "Economy",
  "version": "1.0.0",
  "description": "Economy commands and services.",
  "runtime": {
    "command": "python",
    "args": ["main.py"]
  },
  "capabilities": ["commands", "settings", "events", "jobs"],
  "settings": [
    {
      "key": "currency",
      "label": "Currency",
      "type": "text",
      "defaultValue": "₦",
      "env": "ECONOMY_CURRENCY"
    }
  ],
  "commands": [
    {
      "id": "balance",
      "name": "balance",
      "section": "Economy",
      "description": "Show your balance.",
      "aliases": ["bal"]
    }
  ],
  "jobs": [
    {
      "id": "interest",
      "intervalSeconds": 3600,
      "runOnStart": true
    }
  ]
}
```

`runtime.command` is launched directly without a shell.

Supported capability names are currently `commands`, `settings`, `events`, `jobs`, and `services`. `events` and `jobs` are active in Protocol 1. `services` is reserved for the next host-service layer.

### Runtime choices

For Python, use:

```json
{"command":"python","args":["main.py"]}
```

Bailey does not bundle Python, so `python` must be available on the computer.

For JavaScript, Bailey Studio uses the reserved runtime command `bailey-node`:

```json
{"command":"bailey-node","args":["main.mjs"]}
```

`bailey-node` runs the module with the Node runtime embedded in Bailey Host/Electron. The user does not need a separate Node.js installation. Do not create an executable named `bailey-node`; it is a Bailey Host runtime alias.

Other runtimes can point `runtime.command` at an installed command such as `java`, or at a compiled executable shipped inside the module folder.

Settings declared in the manifest automatically appear under **Configuration**. If a setting declares `env`, Bailey injects its current value into the module process environment when it starts. Secret settings use Bailey's secure-storage path and are not displayed back in plain text.

## Command request

When a module command runs, Bailey writes one JSON line to stdin:

```json
{"protocol":1,"id":"request-id","type":"command.execute","commandId":"balance","context":{"remoteJid":"...","senderJid":"...","text":".balance","args":[]}}
```

The process may stay alive and handle many requests. Bailey currently waits up to 30 seconds for each response.

## Incoming-message event

A module that declares the `events` capability receives ordinary incoming WhatsApp messages, not only command messages.

Bailey sends:

```json
{"protocol":1,"id":"request-id","type":"event.dispatch","event":"message.received","context":{"remoteJid":"...","senderJid":"...","text":"hello","pushName":"Ada","timestamp":1789674000}}
```

Important behavior:

- Events are opt-in. A module without `"events"` in `capabilities` does not receive them.
- Messages sent by the bot itself are not dispatched as `message.received` events.
- Event delivery is independent of command parsing, so a command message may also be seen as an ordinary message event by an event-enabled module.
- The module must send a response for every event request, even when it wants to do nothing. Use an empty `actions` array in that case.
- A disabled Bailey module receives neither commands nor passive message events.

Example no-op event response:

```json
{"protocol":1,"replyTo":"request-id","ok":true,"actions":[]}
```

## Scheduled jobs

A module that declares the `jobs` capability may add fixed-interval jobs to its manifest. `intervalSeconds` must be an integer from 5 seconds through 30 days.

Bailey sends a job request like this:

```json
{"protocol":1,"id":"request-id","type":"job.execute","jobId":"interest","scheduledAt":1789674000000}
```

`scheduledAt` is a Unix timestamp in milliseconds. Set `runOnStart` to `true` when a job should also run once as Bailey starts the scheduler.

Job behavior:

- Bailey owns the timers; the module does not need its own background scheduler.
- Disabled modules do not execute jobs.
- Bailey skips a new run when the same module/job pair is still running from its previous interval.
- Reloading modules stops old timers before new module definitions are loaded.
- Jobs begin only after Bailey's WhatsApp host is initialized, so outbound sends remain host-controlled.

A job has no triggering chat, so it should not return `reply` or `react`. To send a proactive WhatsApp message, return a `send` action with the target JID:

```json
{"protocol":1,"replyTo":"request-id","ok":true,"actions":[{"type":"send","remoteJid":"1203630...@g.us","text":"Daily update is ready."}]}
```

This is useful for episode checks, scheduled economy processing, cleanup/sync tasks, reminders, and notification modules.

## Response

Write one JSON object followed by a newline to stdout:

```json
{"protocol":1,"replyTo":"request-id","ok":true,"actions":[{"type":"reply","text":"Balance: ₦500"}]}
```

Supported actions in Protocol 1 today:

- `{"type":"reply","text":"..."}` — reply to the triggering chat.
- `{"type":"react","emoji":"✅"}` — react to the triggering message.
- `{"type":"send","remoteJid":"...","text":"..."}` — ask Bailey to send a new text message to a specific WhatsApp JID.
- `{"type":"log","level":"info","message":"..."}` — write a module log line. `level` may be `debug`, `info`, `warn`, or `error`.

Bailey remains the only layer that performs WhatsApp actions. External modules never receive the Lia socket or auth state.

For failures:

```json
{"protocol":1,"replyTo":"request-id","ok":false,"error":"Database unavailable"}
```

## Rules

- Do not import or manipulate Lia Baileys from an external module.
- Do not read Bailey's WhatsApp auth/session directory.
- Treat request context as data and return actions for Bailey to perform.
- Keep protocol messages on stdout. Use stderr for diagnostic output; Bailey records it as module diagnostics.
- One line on stdout must contain one complete JSON protocol message.
- Event handlers should return quickly. Use jobs for scheduled/background work instead of blocking message events.

The protocol stays intentionally small. Media actions, host services, and richer storage APIs can be layered on without tying module code to a specific WhatsApp-engine fork.
