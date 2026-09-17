# Bailey Host

Bailey Host turns a normal Windows laptop into a local host for a WhatsApp bot. It is intentionally structured so the bot runtime, desktop UI, configuration, and feature modules are separate pieces.

## Current foundation

- Electron desktop shell that stays alive in the system tray.
- Windows auto-start support.
- Typed module registry.
- Automatic command enable/disable toggles.
- Automatic settings UI generated from module schemas.
- Optional ENV mapping for compatibility with existing bot code.
- Secret settings stored through Electron `safeStorage`; Bailey Host refuses to silently save secrets as plaintext when secure storage is unavailable.
- Windows CI that typechecks, tests, smoke-launches Electron, and builds installer + portable EXE artifacts.

The WhatsApp/Baileys transport is deliberately the next layer, not mixed into the desktop shell.

## Module configuration

A module describes its own settings:

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
      description: "Show weather for a location.",
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

ENV is an adapter, not the UI model. A setting may declare `env: "SOME_NAME"`. Bailey Host stores and validates the typed value locally, then can materialize a runtime environment map for existing Node/Baileys code.

This lets older code keep reading `process.env` while the desktop application presents proper switches, number fields and secret inputs.

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
