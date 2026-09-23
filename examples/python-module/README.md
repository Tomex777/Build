# Python Bailey module example

This module runs as an independent process and uses Bailey Module Protocol 1: newline-delimited JSON on stdin/stdout. Bailey Host owns WhatsApp, engine selection, pairing and session files. The Python process never imports Baileys or receives the WhatsApp socket or credentials.

The example demonstrates a command (`pyhello`), a passive `message.received` event, a persisted daily job, start/stop lifecycle hooks, and provider-neutral storage through `host.call`. The manifest lists requested permissions. A user must grant them in Studio before Bailey performs the corresponding WhatsApp or storage action.

Copy this folder into the Bailey Host modules folder and reload modules in Studio. Run `pyhello` to write a file with `storage.put`; the daily job checks it with `storage.get`. `PYTHON_EXAMPLE_GREETING` is generated from the settings schema and can be changed in Bailey Host.

To try the optional HTTP call from your own extension, request `network.http`, send a `network.request` host call, and add the exact public domain to **Settings → Module network access**. The example deliberately makes no external request by default.

Keep stdout reserved for Protocol 1 messages. Write local diagnostics to stderr or return `log` actions. Bailey manages scheduling, persistence, worker restarts and module data directories.
