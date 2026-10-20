# ON "CONTINUE" — RESUME EXISTING PR, UNBLOCK THE COMBINED APK

Read `night-browser/docs/integration-resume.md`, latest Issue #460 messages and the current unified integration candidate #468. Continue your existing open agent PR/branch; **do not create another unmerged copy** unless Agent 1 explicitly requests a separate narrow bugfix. Verify your module is wired in the real combined browser, address actual build/API26/36/runtime/security errors, coordinate shared-file edit ownership, request review/merge and supply tested evidence. Agent 10 owns independent black-box QA of the combined SHA; a report-only workflow's green status is not acceptance. No promises of background work after chat ends.

---

# WAVE 2: NIGHT Browser Agent 6 — Real downloads and useful Library

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-06-library` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 6"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PR #432 adds repository/download manager but is **not wired or fully verified**. Finish full UI wiring with Agent 1 and authenticated Gecko response streaming with A4 rather than silently re-requesting URLs. Show real download bytes/progress, MIME/storage ownership, open/share/export/retry/delete, cancel where supported. Library should support bookmark edits/folders/search, actual history, reading list, local retention and privacy separation from private sessions. Fix Android 8 vs modern SAF/MediaStore storage differences; large interrupted files and redirects. Do not fake progress or claim pause/resume without backend capability.

### Acceptance (real APK / real websites)

Test large/partial, image and PDF HTTPS downloads, logged-in download if a test fixture exists, error/retry, file opens, reboots/relaunch and offline browsing record; actual checksum/file bytes where possible. No private items silently entered into public download history.

### Dependency coordination

A4 owns Gecko response stream, A3 private state, A5 graph data, A7 archive files, A1 UI entrypoints.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

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
