# Python Bailey module example

This is a minimal code-backed Bailey Host module written in Python.

## How it connects

Bailey Host owns WhatsApp and Lia Baileys. The Python process does not import or access the WhatsApp engine directly.

Bailey launches the command declared in `bailey.module.json` and communicates with the process using newline-delimited JSON over stdin/stdout (Bailey Module Protocol 1).

For a command invocation Bailey sends a request shaped like:

```json
{"protocol":1,"id":"request-id","type":"command.execute","commandId":"pyhello","context":{"remoteJid":"...","senderJid":"...","text":".pyhello","args":[]}}
```

The module replies with actions:

```json
{"protocol":1,"replyTo":"request-id","ok":true,"actions":[{"type":"reply","text":"Hello"},{"type":"react","emoji":"🐍"}]}
```

Bailey performs those WhatsApp actions on the module's behalf.

## Install locally

1. Open **Bailey Host → Studio → Open modules folder**.
2. Copy this whole `python-module` folder into that directory.
3. Make sure `python` is available on the laptop's PATH.
4. Restart Bailey Host.
5. The module and its configuration will appear in **Modules** and **Configuration**.

The `PYTHON_EXAMPLE_GREETING` ENV value is generated automatically from the module's settings schema. Users edit the value through Bailey Host; the Python worker simply reads the environment variable.
