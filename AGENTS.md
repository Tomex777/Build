# Repository agent navigation

This repository contains several unrelated projects. **Do not apply NIGHT/MSCC deployment instructions to unrelated projects.**

## NIGHT/MSCC (WhatsApp bot + Azure)

For any change to `mscc/**`, the NIGHT bot, or `.github/workflows/mscc-azure.yml` / `.github/workflows/night-release-batch.yml`:

1. **Read [`mscc/AGENTS.md`](mscc/AGENTS.md) before editing, merging, or deploying.** It is the source of truth for MSCC branches, tests, release batching, and Azure approvals.
2. Follow that file even when the request arrives in another ChatGPT conversation or coding-agent session. Prior conversation history is not required to discover the process.
3. Do not assume that any ChatGPT chat was proactively notified of the release policy. Agents must inspect the repository's current `mscc/AGENTS.md` and workflow files.
4. Keep other apps' build, signing, and deployment flows independent. In particular, NIGHT Play website/game branches and mobile apps are *not* automatically part of an MSCC Azure batch.

Documentation-only MSCC commits are **not** permission to deploy. Only the validated release-batch manifest (or an explicitly requested manual deployment) should request the protected `mscc-production` approval.
