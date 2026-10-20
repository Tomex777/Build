# NIGHT Browser — Agent 3: Tabs, thumbnails, incognito and restoration

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-03-tabs`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-03-tabs` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Implement polished Chrome-style two-column tab preview cards showing genuine rendered site pixels; audit existing capturePixels prototype for compile/runtime API compatibility, bitmap lifetime and private-data leakage. Add tab switch/close/undo, responsive grid, robust session restoration after process death, low-memory behavior and real private mode with isolated cookies/history/storage—not a cosmetic switch. Keep active Gecko sessions stable during Compose recomposition.

## Evidence required before reporting completion

Actually browse 10+ sites, switch/close/reopen/restart, inspect tab previews; verify private login cookies do not leak into normal tabs or persisted history. Record memory and crash testing.

## Collaboration / shared-file rules

Coordinate Gecko runtime/session hooks with Agent 4; Agent 1 owns conflicting MainActivity wiring.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
