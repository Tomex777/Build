# ON "CONTINUE" — RESUME EXISTING PR, UNBLOCK THE COMBINED APK

Read `night-browser/docs/integration-resume.md`, latest Issue #460 messages and the current unified integration candidate #468. Continue your existing open agent PR/branch; **do not create another unmerged copy** unless Agent 1 explicitly requests a separate narrow bugfix. Verify your module is wired in the real combined browser, address actual build/API26/36/runtime/security errors, coordinate shared-file edit ownership, request review/merge and supply tested evidence. Agent 10 owns independent black-box QA of the combined SHA; a report-only workflow's green status is not acceptance. No promises of background work after chat ends.

---

# WAVE 2: NIGHT Browser Agent 8 — User-consented AI Universal Search, genuinely useful

**Priority change:** This supersedes first-wave assignments below where outdated. **Same app, same integration branch `feature/night-browser-multi-agent-20261020`**, short-lived `agent/browser-v2-08-ai-search` branch. In your GitHub-connected chat, saying **"You are NIGHT Browser Agent 8"** or "Continue NIGHT Browser Wave 2" means: read this file, `night-browser/AGENTS.md`, `night-browser/docs/agent-backlog.md`, `night-browser/agents/README.md`, current source/PRs and [Issue #460](https://github.com/Tomex777/Build/issues/460) latest comments. Claim task on #460, coordinate shared edit file lock with Agent 1, implement immediately and create a PR to `feature/night-browser-multi-agent-20261020`. Don't claim other agents are automatically running. **Don't create another disconnected variant of an existing specialist module.**

### Substantial improvement assignment

PR #431 implements opt-in user-key provider code but is **not user-facing in integrated shell**. Complete verified provider UI and on-device privacy contract with A1: no credentials in GitHub, user can choose source scope, explicit external-request confirmation, search results must cite real local Journeys/Library pages, never hallucinate or pretend keywords are model inference. Harden remote model errors, streaming/timeouts, prompt injection within page content, cost/status UX and opt-out/deletion. Add a local helpful regular search fallback that is clearly NON-AI, distinct from model search. Never index private sessions or silently upload page text.

### Acceptance (real APK / real websites)

With explicitly configured test provider, perform true API call and check source-grounded citations, confidence/failure; with no key show truthful disabled setup; network failure, opt-out/no-upload, private exclusion and protected key handling tested. Do not claim working inference just because SDK compiled.

### Dependency coordination

A1 shell wiring, A3 private-mode flags, A5/A6 user-selected search index, A10 privacy QA.

**Required loop:** inspect baseline → reproduce failure/identify quality gap → communicate exact shared API → implement → build → Android install → interact with actual sites → screenshot/logcat/perf → fix → retest → small PR → ask A10 for independent QA → integrate via A1 → next task. Distinguish real installed tests from CI-only; no unattended execution beyond the active session.

---

## Original first-wave role brief (background, lower priority than Wave 2)

# NIGHT Browser — Agent 8: AI-powered Universal Search

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-08-universal-search`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-08-universal-search` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Make Universal Search genuinely model-backed, not keyword ranking dressed as AI. Build consent-first data flow, local/on-device vs configured provider options, opt-in and revoke, private-mode exclusion, permissions per indexed source, citations to source pages and non-fabricated failure states. Never hardcode API secrets or silently send browsing content/history. If provider unavailable, display truthful not-configured state; don't fake an answer.

## Evidence required before reporting completion

Demonstrate an actual model response grounded in permitted local/indexed data if a provider is configured; also test no-provider, offline, opt-out and sensitive content suppression. Distinguish integration code from successfully tested inference.

## Collaboration / shared-file rules

Consult Agents 2 (search UI), 4 (safe page access), 5/6 (local data), and 10 (privacy testing).

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
