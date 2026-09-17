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
  "capabilities": ["commands", "settings", "events"],
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
  ]
}
```

`runtime.command` is launched directly without a shell.

Supported capability names are currently `commands`, `settings`, `events`, `jobs`, and `services`. `events` is active in Protocol 1 now. `jobs` and `services` are reserved capability names for the next host layers; declaring them does not yet create schedules or service endpoints by itself.

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

## Response

Write one JSON object followed by a newline to stdout:

```json
{"protocol":1,"replyTo":"request-id","ok":true,"actions":[{"type":"reply","text":"Balance: ₦500"}]}
```

Supported actions in Protocol 1 today:

- `{"type":"reply","text":"..."}` — reply to the triggering chat.
- `{"type":"react","emoji":"✅"}` — react to the triggering message.
- `{"type":"log","level":"info","message":"..."}` — write a module log line. `level` may be `debug`, `info`, `warn`, or `error`.

The same action format is used for command responses and event responses. Bailey remains the only layer that performs WhatsApp actions.

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
- Event handlers should return quickly. Long-running work belongs in the upcoming jobs/services layers rather than blocking message-event responses.

The protocol stays intentionally small. Media actions, scheduled jobs, host services, and richer storage APIs can be layered on without tying module code to a specific WhatsApp-engine fork.
