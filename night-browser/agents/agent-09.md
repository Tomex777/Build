# ON "CONTINUE" — RESUME EXISTING PR, UNBLOCK THE COMBINED APK

Read `night-browser/docs/integration-resume.md`, latest Issue #460 messages and the current unified integration candidate #468. Continue your existing open agent PR/branch; **do not create another unmerged copy** unless Agent 1 explicitly requests a separate narrow bugfix. Verify your module is wired in the real combined browser, address actual build/API26/36/runtime/security errors, coordinate shared-file edit ownership, request review/merge and supply tested evidence. Agent 10 owns independent black-box QA of the combined SHA; a report-only workflow's green status is not acceptance. No promises of background work after chat ends.

---

# WAVE 2: NIGHT Browser Agent 9 — Peek, per-origin Site Studio and adaptive browsing

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-09-site-studio` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 9"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PR #434 contains Site Studio and Peek modules but they are **not yet connected to the app**. Integrate via agreed A4 Gecko hooks and A1 shell, then ensure real per-origin CSS/JS edits apply only to the opted-in domain, survive reload, can be previewed, reverted, disabled and deleted. Prevent CSP/cross-origin leakage; no hidden privileged script injection or log exposure. Peek should feel instant but use safely disposable Gecko sessions, back navigation within peek, promotion to real tab without corrupting main history or silently downloading. Audit memory pressure and low battery behavior, UI and error states.

### Acceptance (real APK / real websites)

On emulator, long-press 5 real links, dismiss/promote and confirm old history; edit one origin, verify another unchanged after restart; test user undo/reset and crash/memory cleanup. Real screenshots and tests, not concept UI.

### Dependency coordination

A4 extension session bridge, A3 tab lifecycle, A1 route wiring; respect original UI design A2.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 9: Peek, Site Studio and adaptive browsing

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-09-peek-site-studio`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-09-peek-site-studio` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Improve Peek disposable previews and promote-to-tab without breaking origin/history or leaving sessions/downloads. Implement per-site user-controlled CSS/JS customization in Site Studio with scoped origins, enable/disable, live preview, undo/reset and secure persistence; no hidden injection into other origins. Tune memory economy, page zoom/accessibility and site settings with robust fallback states.

## Evidence required before reporting completion

Test multiple Peek dismiss/promotions, original tab backstack, cross-site CSS isolation and reset after restart; capture UI and crash/memory evidence.

## Collaboration / shared-file rules

Use Agent 4 Gecko hooks, Agent 3 tab promotion and Agent 1 UI wiring; coordinate before editing shared controller.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
