# MISE — RESUME EXISTING WORK, INTEGRATE AND COMPLETE

When a user says **"Continue"**, **"Resume"**, or **"Keep going"** in an existing MISE agent chat, read `artist-scene-studio/docs/integration-resume.md`, the current agent role, canonical branch head and all latest **GitHub Issue #440** comments before anything else. Resume your existing open PR rather than create another detached development wave. Agent 1 is responsible for conflict-safe integration, making one compiled/running APK and coordinating independent Android QA; other agents unblock integration and fix existing PRs; Agent 10 is independent critical tester. A green CI badge or submission of ten PRs is not completion. Chats do not continue operating in the background after ending.

# MISE — launch ten communicating agents

Open **ten independent GitHub-connected coding chats**. In MISE context, type **"You are MISE Agent 1"**, **"You are MISE Agent 2"**, … **"You are MISE Agent 10"**. Each agent reads its own Markdown brief from this branch, the shared rules and **[issue #440](https://github.com/Tomex777/Build/issues/440)**, then codes.

If the chat has no project context, use: `You are MISE Agent N. Read Tomex777/Build artist-scene-studio/agents/agent-NN.md and AGENTS.md from integrate/mise-studio-completion-20261020, plus GitHub issue #440, then implement.`

An agent needs a GitHub connection; role Markdown does not get silently injected into all future chats and does not spawn automatic processes.

## Shared codebase — not one shared writable branch

All developers share the **same source repository, architecture, live issue conversation, integrated app and release goal**. Each uses a short-lived branch/worktree for in-progress code and submits small PRs to `integrate/mise-studio-completion-20261020`. Agent 1 merges continuously; everyone syncs with that branch after a PR is merged.

This gives the communication benefits of one codebase WITHOUT the lost edits, corrupted history, conflicting builds and race conditions of ten simultaneous writers to a single branch.

| Agent | Role / brief | Primary responsibility |
|---|---|---|
| 1 | [Coordinator](agent-01.md) | Integration, locks, reviews, release acceptance |
| 2 | [Editor UX](agent-02.md) | Professional Compose workspace, tools and inspector |
| 3 | [Characters](agent-03.md) | Authentic anime models, materials, face/hair/accessories |
| 4 | [Pose & animation](agent-04.md) | Rigging, IK, keyframes, playback |
| 5 | [Environment](agent-05.md) | Real terrains, PBR ground, sun/sky and scenery |
| 6 | [Assets](agent-06.md) | Model import, asset browser, props/materials |
| 7 | [Rendering](agent-07.md) | Cameras, lights, renderer quality, PNG export |
| 8 | [Core state](agent-08.md) | Project persistence, schema, hierarchy and undo |
| 9 | [Android reliability](agent-09.md) | Memory, frame times, lifecycle, builds/signing |
| 10 | [Independent QA](agent-10.md) | Android API 26/36 touch and true scene screenshot QA |

### Five mandatory actions every session

1. Read `AGENTS.md`, your role file, `docs/agent-backlog.md`, `docs/definition-of-done.md`, and the latest messages on **issue #440**.
2. Post an **[MISE A#][CLAIM]** check-in with exact paths/interfaces, ask Agent 1 to resolve overlaps, and make an agent-owned branch from integration.
3. Implement a specific accepted task. Use real Android tools to compile, render, save and touch-test wherever available; do not mislabel unit tests as rendered scene checks.
4. Post an **[MISE A#][READY]** comment and create a PR *targeting integration*, including real tests/evidence, photos/screenshots if captured, files/interfaces and integration notes.
5. After feedback/merge, **choose the next unblocked task** from the backlog. At any session cutoff write a resumable handoff and do not promise work will keep running without an active session.

Agent 1 must land compatible tested PRs frequently, not wait until all nine are "done" or generate another planning document. Completion is **one tested APK**, not 10 PRs.
