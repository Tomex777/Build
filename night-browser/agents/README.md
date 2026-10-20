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
