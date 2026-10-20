# MISE — RESUME EXISTING WORK, INTEGRATE AND COMPLETE

When a user says **"Continue"**, **"Resume"**, or **"Keep going"** in an existing MISE agent chat, read `artist-scene-studio/docs/integration-resume.md`, the current agent role, canonical branch head and all latest **GitHub Issue #440** comments before anything else. Resume your existing open PR rather than create another detached development wave. Agent 1 is responsible for conflict-safe integration, making one compiled/running APK and coordinating independent Android QA; other agents unblock integration and fix existing PRs; Agent 10 is independent critical tester. A green CI badge or submission of ten PRs is not completion. Chats do not continue operating in the background after ending.

# Tomex777/Build — multi-project agent entrypoint

This repository contains several projects. **A generic "You are Agent N" must resolve the current project context first**, not silently assign a role from another project.

## MISE agent routing

If this chat is explicitly about **MISE** or the user says **"You are MISE Agent N"**, where N=1…10:
1. Read `artist-scene-studio/agents/README.md` on `integrate/mise-studio-completion-20261020`.
2. Read `artist-scene-studio/AGENTS.md`, the matching `artist-scene-studio/agents/agent-NN.md` and `artist-scene-studio/docs/agent-backlog.md` on that branch.
3. Read GitHub Issue #440, including its latest comments. This is the live inter-agent message bus.
4. Inspect current relevant source, branches and PRs, claim your work and begin actual coding.

Canonical MISE code path: `artist-scene-studio/`. MISE production 1.0 is on `main`; ongoing advanced work is isolated on `integrate/mise-studio-completion-20261020`. Other projects (NIGHT Play, NIGHT Browser, etc.) retain their own project instructions.

These role files do NOT run persistent autonomous agents. ChatGPT sessions cannot message each other directly; agents coordinate through GitHub issue comments, PRs, the integration branch and the durable task ledger.
