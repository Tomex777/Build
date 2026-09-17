# Bailey Host

Bailey Host turns a normal Windows laptop into a local host for a WhatsApp bot. The WhatsApp engine, desktop UI, configuration, chat history, and feature modules are kept as separate layers so the bot can grow without turning into one monolithic codebase.

## Current foundation

- Electron desktop shell that stays alive in the system tray.
- Windows auto-start support.
- Replaceable Lia Baileys engine management with install, update, rollback, pairing, and runtime status.
- Local Chats view with persisted conversations, search, incoming/outgoing text, timestamps, and deduplication.
- Typed module registry with module-level and command-level enable/disable controls.
- Automatic settings UI generated from module schemas.
- Optional ENV mapping for compatibility with existing bot code.
- Secret settings stored through Electron `safeStorage`; Bailey Host refuses to silently save secrets as plaintext when secure storage is unavailable.
- Generated `.menu` output based on the current prefix, sections, renamed commands, aliases, and enabled state.
- Visual command editing for declarative reply/react actions.
- Bailey Studio file editing, JavaScript/Python module scaffolding, modules-folder access, and live module reload.
- Language-neutral external module protocol over JSONL. Modules can be written in Python, JavaScript, Java, Go, Rust, or another process runtime.
- External module commands, passive incoming-message events, and Bailey-managed fixed-interval background jobs.
- Host-controlled outbound actions so external modules do not receive the Lia socket or WhatsApp auth state.
- Windows CI that typechecks, tests, smoke-launches Electron, installs a real Lia engine, exercises a packaged JavaScript module, and builds a portable EXE artifact.

## External modules

External modules live in Bailey Host's Modules folder and declare a `bailey.module.json` manifest. Bailey owns the WhatsApp connection and launches the module as a child process. Requests and responses are newline-delimited JSON.

Protocol 1 currently supports:

- `commands` — commands such as `.balance` or `.anime`.
- `settings` — typed settings that automatically appear in Configuration and can map to environment variables.
- `events` — passive `message.received` delivery for ordinary incoming WhatsApp messages.
- `jobs` — Bailey-managed fixed-interval background work with overlap protection and optional run-on-start behavior.

External modules return actions for Bailey to perform, including reply, react, proactive text send, and logging. They do not import Lia Baileys or access Bailey's WhatsApp auth/session directory.

See [`docs/MODULE_PROTOCOL.md`](docs/MODULE_PROTOCOL.md) for the wire protocol and the Python example under [`examples/python-module`](examples/python-module).

## Module configuration

A built-in module describes its own settings:

```ts
import { defineCommand, defineModule } from "../../core/module";
import { setting } from "../../shared/config-schema";

export default defineModule({
  id: "weather",
  name: "Weather",
  version: "1.0.0",
  settings: [
    setting.toggle("enabled", "Weather enabled", true, {
      env: "WEATHER_ENABLED",
    }),
    setting.secret("apiKey", "API key", {
      env: "WEATHER_API_KEY",
    }),
    setting.number("timeout", "Timeout", 10, {
      env: "WEATHER_TIMEOUT",
      min: 1,
      max: 60,
    }),
    setting.select("units", "Units", "metric", [
      { label: "Metric", value: "metric" },
      { label: "Imperial", value: "imperial" },
    ], {
      env: "WEATHER_UNITS",
    }),
  ],
  commands: [
    defineCommand({
      name: "weather",
      section: "Utility",
      description: "Show weather for a location.",
      execute: async ({ reply }) => {
        await reply("Weather module ready.");
      },
    }),
  ],
});
```

Bailey Host turns those declarations into UI controls automatically:

- `toggle` → on/off switch
- `text` → text field
- `secret` → masked secret field
- `number` → numeric field with optional limits
- `select` → dropdown

Every command automatically receives its own enable/disable toggle. A module also automatically receives a master enable/disable toggle.

## ENV compatibility

ENV is an adapter, not the UI model. A setting may declare `env: "SOME_NAME"`. Bailey Host stores and validates the typed value locally, then can materialize a runtime environment map for existing Node/Python/bot code.

This lets older modules keep reading environment variables while the desktop application presents proper switches, number fields, selections, and secret inputs.

## Development

```bash
npm install
npm run typecheck
npm test
npm start
```

Build Windows artifacts:

```bash
npm run dist:win
```

## Next host layers

The next protocol work is aimed at richer host services and storage APIs. Those layers are what larger modules such as economy systems, anime/manga workflows, media processors, and blob-backed features can build on without taking ownership of Bailey's WhatsApp engine.
