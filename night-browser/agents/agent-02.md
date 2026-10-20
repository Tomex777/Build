# NIGHT Browser — Agent 2: Home, Omnibar and navigation UX

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-02-home`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-02-home` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Replace the rejected oversized-wordmark Home with a polished Omni Browser-inspired start page. Inspect current quick-access implementation, improve it with functional user-editable sites, favicons, recent visits, Journey entry and good empty states. Make the search/address field behave correctly on keyboard, clipboard, URL or search terms, focus and submit. Clean up toolbar/navigation, no duplicate controls, avoid Android status/navigation bar collisions. Use original Compose assets rather than copying Omni GPL code.

## Evidence required before reporting completion

Install browser. Visually compare at small/large width. Use shortcuts, enter URLs and queries, navigate back/forward, rotate and use keyboard. Share actual screenshots and working behaviour, not static mocks.

## Collaboration / shared-file rules

Agent 3 owns tab grid; Agent 4 owns Gecko controller. Extract Home UI into separate composables; reserve MainActivity integration with Agent 1.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
