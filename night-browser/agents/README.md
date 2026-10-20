# NIGHT Browser — Wave 2 large-scale integration and polishing

This is a **continuation**, not another prototype. Browser integration branch: `feature/night-browser-multi-agent-20261020`, head at baseline `1f28e9d5cedf`; updated heads must be checked live. Existing Home and agent1 coordination PRs merged; browser PR #439 is still **draft partial assembly**, and the remaining specialist PRs are not integrated. The first-wave docs after this introductory section are archival where contradicted.

**Launch 10 GitHub-connected chats:** Say `You are NIGHT Browser Agent 1` through `You are NIGHT Browser Agent 10`. Existing chats may say `Continue NIGHT Browser Wave 2: read agent-NN.md from feature/night-browser-multi-agent-20261020 and issue #460, then implement the next unblocked work.` Do not assume every ChatGPT chat can access GitHub automatically.

## Shared system / mandatory check-in

- **Single continuously integrated Android codebase**, with short-lived per-agent branches/worktrees. All PRs target `feature/night-browser-multi-agent-20261020`, not main. No concurrent edits to the same controller/manifest without Agent 1's agreed contract.
- **Cross-agent communication:** GitHub [Issue #460](https://github.com/Tomex777/Build/issues/460). Read latest comments at chat start, before modifying shared API, and after READY/INTEGRATED. Post `[BROWSER A#][CLAIM|DESIGN|BLOCKED|HANDOFF|READY|VERIFIED|INTEGRATED]` with owned paths, branch+PR, actual compile/install/real-site evidence, who needs to respond and next action. GitHub does not make separate chats automatically communicate in real-time; read and acknowledge relevant comments.
- Before coding: inspect PR #439 (partially assembled new modules), PR #441 Home QA, agent PRs #426–#434, draft #422 app-ID divergence, project README and the actual latest integration files.
- First priority **integrate what already exists**. Then run **the actual compiled app**, visit real sites, test, fix defects and improve design/latency. Build without UI tests is insufficient. Each release blocker must be closed via code + retest.
- Preserve browser package `com.night.browser`, minSdk26, user data and credentials, non-AI Journeys, honest PDF-only versus interactive offline archives, and opt-in real model-backed Universal Search.
- Agent 1 merges as soon as a small PR is verified instead of waiting for a bundle of ten. Continue with the next unblocked improvement during an active session; leave durable handoff at session limit; no promises of autonomous "nonstop" work.

| Agent | Wave-2 focus | Brief |
| --- | --- | --- |
| 1 | Coordinator: produce ONE working browser APK | [agent-01.md](agent-01.md) |
| 2 | Home, omnibar and visual design mastery | [agent-02.md](agent-02.md) |
| 3 | Stable tab grid, process restoration and true incognito | [agent-03.md](agent-03.md) |
| 4 | Production GeckoView site compatibility | [agent-04.md](agent-04.md) |
| 5 | Beautiful correct automatic branching Journeys | [agent-05.md](agent-05.md) |
| 6 | Real downloads and useful Library | [agent-06.md](agent-06.md) |
| 7 | Versioned Time Machine with trustworthy offline behavior | [agent-07.md](agent-07.md) |
| 8 | User-consented AI Universal Search, genuinely useful | [agent-08.md](agent-08.md) |
| 9 | Peek, per-origin Site Studio and adaptive browsing | [agent-09.md](agent-09.md) |
| 10 | Independent app-wide Android quality and security gate | [agent-10.md](agent-10.md) |

## Release contract

One installable native Android APK with reliable GeckoView page compatibility, excellent responsive UX, session restore/real private isolation, true Library/download files, accurate Journeys, usable archived versions, safely scoped Peek/Site Studio, verified optional AI inference and real API26/API36 cross-feature testing. No production merge/release before app-level review.

---

# NIGHT Browser — one-line ten-agent handoff

**User workflow:** Open ten independent coding chats with GitHub access. In each NIGHT Browser chat, type **"You are Agent 1"**, **"You are Agent 2"**, etc. The agent must read its role document, inspect the repository, and implement its assigned code instead of responding only with a plan.

**Important:** Separate ChatGPT chats do not automatically read GitHub repository files merely because they exist. Each agent must fetch the instructions through GitHub. If a new chat lacks browser context, use this reliably specific one-liner instead:
`You are NIGHT Browser Agent N. Read Tomex777/Build night-browser/agents/README.md on feature/night-browser-multi-agent-20261020, then execute your role.`

Read in order: root `AGENTS.md` → `night-browser/AGENTS.md` → your `night-browser/agents/agent-NN.md` → `night-browser/docs/agent-backlog.md` → actual Kotlin source / CI / open PRs.

## Roles

| Agent | Role file | Domain |
| --- | --- | --- |
| 1 | [agent-01.md](agent-01.md) | Coordination, ownership, PR review, integration |
| 2 | [agent-02.md](agent-02.md) | Omni-inspired Home, address/search UI |
| 3 | [agent-03.md](agent-03.md) | Chrome-like tab grid, previews, incognito |
| 4 | [agent-04.md](agent-04.md) | GeckoView and real-site compatibility |
| 5 | [agent-05.md](agent-05.md) | Automatic Journeys tree |
| 6 | [agent-06.md](agent-06.md) | Library, history, downloads |
| 7 | [agent-07.md](agent-07.md) | Time Machine, versioned snapshots |
| 8 | [agent-08.md](agent-08.md) | Opt-in real AI Universal Search |
| 9 | [agent-09.md](agent-09.md) | Peek, Site Studio, adaptive browsing |
| 10 | [agent-10.md](agent-10.md) | Independent install/playthrough QA |

## Required operating procedure — every agent

- **Repository:** `Tomex777/Build`; browser source `night-browser/`. Current integration branch `feature/night-browser-multi-agent-20261020`. Existing draft PR #384 is the first prototype; draft PR #421 is the multi-agent foundation.
- Read source and verify existing status before changing it. The current Home and thumbnail tab grid have preliminary changes that **still require build and real-device verification**.
- Claim an item from the ledger; do not modify the same Kotlin/Gradle/manifest files simultaneously with another agent. Ask Agent 1 to arbitrate shared-file ownership; extract independent UI files or use staged integration.
- Create a separate branch from current integration, convention `agent/browser-NN-topic`. Never push to another agent's branch, to `main`, or to integration without coordinator agreement.
- Actually implement, compile, install and **interact with the running browser**. Investigate errors. Do not equate builds/screenshot-only checks with full functionality.
- Submit a PR targeting the integration branch, and report branch, commits, test results, screenshots/logs, blockers and next action. Agent 1 integrates only after verification; check pending PRs throughout development rather than hoarding unmerged branches.
- No fabricated agents/parallelism. Opening the chats is the user's action; a task ledger is not evidence that ten people are currently working.
- Respect ABR, Android minSdk 26, `com.night.browser`, privacy and user-approved Omni-inspired look. Reference Omni Browser's GPL-3.0 design legally: original implementation preferred; copying code/assets requires GPL compliance and conscious approval.
