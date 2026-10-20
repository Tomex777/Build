# NIGHT Browser — Agent 6: Library, history and download management

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-06-library`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-06-library` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Build a full functional Library, not just bookmark rows: bookmarks organization, search and editing, browsing history, reading list if useful, downloads with true states, MIME, file handling, errors/retry and storage controls. Implement a real Android download pipeline with proper Storage Access Framework/MediaStore integration; do not fake progress, resuming or an offline indicator.

## Evidence required before reporting completion

From real sites download/open PDF and image files, induce failure/retry, save/edit/open/remove bookmarks and history, verify persistence and private-data isolation. Capture screenshots and tested file types.

## Collaboration / shared-file rules

Agent 4 handles Gecko download hooks, 5 Journey storage, 7 Time Machine files; agree interfaces before touching shared persistence.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
