# JavaScript Bailey module example

This module runs as an independent process. Bailey Host owns WhatsApp, engine selection, pairing and session files. The module communicates with Bailey using Protocol 1 JSONL on stdin/stdout and never receives a WhatsApp socket or credentials.

The example includes a command, a passive message event, a daily scheduled job, start/stop lifecycle hooks, provider-neutral storage calls, and allowlisted host-mediated HTTPS. Its manifest requests permissions; a user must grant those permissions in Studio before Bailey performs the corresponding action. Add `example.com` to this module's allowlist in **Settings → Module network access** for the sample network call to work.

Copy this folder into Bailey Host's modules folder, then reload modules in Studio. Run `jshello` to save a file through Bailey's storage service. The daily job reads that file and makes a small `HEAD` request through Bailey's network service.

Bailey handles worker restarts and scheduled-job persistence. Keep stdout reserved for protocol messages; write any local diagnostics to stderr or return `log` actions.
