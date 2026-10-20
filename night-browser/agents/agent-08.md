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
