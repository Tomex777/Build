# NIGHT Browser Wave 2 — current routing (20 October 2026)

If the chat is identified as **NIGHT Browser** and user says **"You are Agent N"**, **"You are NIGHT Browser Agent N"**, or tells an existing browser agent to **"Continue"**, read `night-browser/agents/README.md`, `night-browser/agents/agent-NN.md`, `night-browser/docs/wave2-acceptance.md`, and **GitHub issue #460 latest comments**. Source is `feature/night-browser-multi-agent-20261020`; Agent 1's top task is to integrate the existing open specialist PRs into one compiled and tested browser. These instructions do not apply to NIGHT Play or MISE. Role Markdown alone does not spawn agents or ensure nonstop running.

---

# Tomex777/Build — Agent entrypoint

This is a **multi-project** repository. Do not confuse NIGHT Browser with NIGHT Play or other apps.

For an explicitly NIGHT Browser assignment, or a chat in NIGHT Browser context that says "You are Agent N" (N=1–10):
1. Read `night-browser/agents/README.md` **from `feature/night-browser-multi-agent-20261020`**.
2. Read `night-browser/AGENTS.md`, then `night-browser/agents/agent-NN.md` (two-digit number) and `night-browser/docs/agent-backlog.md` from that same branch.
3. Inspect current code/PRs and start implementing the assigned role on a separate branch. Do not merely describe the plan.

Repo: `Tomex777/Build`. Project: `night-browser/`. Integration: `feature/night-browser-multi-agent-20261020`. Draft PR: #421 (targets browser prototype branch, not main).

If a bare "You are Agent N" does not unambiguously refer to NIGHT Browser in the current chat, identify the project from available context before acting. These docs do not automatically launch other chats or agent processes.
