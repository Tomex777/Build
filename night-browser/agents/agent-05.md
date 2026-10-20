# NIGHT Browser — Agent 5: Automatic Browsing Journeys

Repo: `Tomex777/Build`; path: `night-browser/`; integration branch: `feature/night-browser-multi-agent-20261020`; your isolated branch: `agent/browser-05-journeys`.

This is an **active NIGHT Browser coding assignment**, not a prompt-writing or planning task. On selection, use the connected GitHub tools to read root `AGENTS.md`, `night-browser/AGENTS.md`, `night-browser/agents/README.md`, this role file and `night-browser/docs/agent-backlog.md` from **`feature/night-browser-multi-agent-20261020`**, then inspect real source/PRs and implement immediately.

**Every agent:** isolate work on `agent/browser-05-journeys` created from the browser integration branch; claim file ownership with Agent 1 before shared changes; create a PR **targeting the browser integration branch**, not main; report build, install and actual interaction tests separately. Never claim to have spawned other agents. Follow ABR, original clean UI, minSdk 26, package `com.night.browser`, privacy and proper GeckoView operation. CI passing is not equal to done.

## Assigned ownership and work

Journeys remain a NON-AI feature. Audit existing JourneyStore parent-node logic: correct branch parent when going back/forward, jumping nodes, redirects, reloading, new tabs and app restarts. Build a readable real graph/tree UI, search/filter, rename, resume and delete; migrate existing data rather than dropping history. Reduce duplicate noise and sensitive/private URL capture.

## Evidence required before reporting completion

Test A→B→C, back A→D, revisit another node, open child tab, restart; verify exact parents and visual tree on narrow Android screen. Assert private visits not stored.

## Collaboration / shared-file rules

Coordinate JourneyStore data model with Agent 6/7, Gecko navigation signals with Agent 4, and UI wiring with Agent 1.

## Start now

Read source, review backlog and outstanding PRs, create your own branch, implement the most urgent unblocked work within scope, and open a reviewable PR. If tools are unavailable, state the limitation rather than claiming the code changed.
