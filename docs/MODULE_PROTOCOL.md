# Bailey Module Protocol 1

Bailey Host owns the WhatsApp connection. Code-backed modules run as separate processes and communicate with Bailey over newline-delimited JSON (JSONL) on stdin/stdout.

This keeps modules independent of Lia Baileys internals and allows modules to be written in Python, Node.js, Java, Go, Rust, or any runtime that can read and write JSON lines.

## Module folder

Each module lives in its own directory under Bailey Host's Modules folder:

```text
modules/
└── economy/
    ├── bailey.module.json
    ├── main.py
    └── ...
```

Bailey Host → Studio → **Open modules folder** opens this location.

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
  "capabilities": ["commands", "settings"],
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

`runtime.command` is launched directly without a shell. Bailey does not bundle Python; `python` must be available on the machine for a Python module. Compiled modules can point at their own executable instead.

Settings declared in the manifest automatically appear under **Configuration**. If a setting declares `env`, Bailey injects its current value into the module process environment when it starts. Secret settings use Bailey's secure-storage path and are not displayed back in plain text.

## Command request

When a module command runs, Bailey writes one JSON line to stdin:

```json
{"protocol":1,"id":"request-id","type":"command.execute","commandId":"balance","context":{"remoteJid":"...","senderJid":"...","text":".balance","args":[]}}
```

The process may stay alive and handle many requests. Bailey currently waits up to 30 seconds for each command response.

## Response

Write one JSON object followed by a newline to stdout:

```json
{"protocol":1,"replyTo":"request-id","ok":true,"actions":[{"type":"reply","text":"Balance: ₦500"}]}
```

Supported actions in Protocol 1 today:

- `{"type":"reply","text":"..."}` — reply to the triggering chat.
- `{"type":"react","emoji":"✅"}` — react to the triggering message.
- `{"type":"log","level":"info","message":"..."}` — write a module log line. `level` may be `debug`, `info`, `warn`, or `error`.

For failures:

```json
{"protocol":1,"replyTo":"request-id","ok":false,"error":"Database unavailable"}
```

## Rules

- Do not import or manipulate Lia Baileys from an external module.
- Do not read Bailey's WhatsApp auth/session directory.
- Treat the request context as data and return actions for Bailey to perform.
- Keep protocol messages on stdout. Use stderr for diagnostic output; Bailey records it as module diagnostics.
- One line on stdout must contain one complete JSON protocol message.

The protocol is intentionally small. Future versions can add media actions, richer storage APIs, events, scheduled jobs, and services without tying module code to a specific WhatsApp-engine fork.
