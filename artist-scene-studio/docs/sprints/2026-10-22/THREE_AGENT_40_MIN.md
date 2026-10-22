# MISE Artist Studio — three agents, exactly 40 minutes each

**Sprint date:** 2026-10-22 · **Priority:** unfinished native Android artist studio · **Status:** TASK INSTRUCTIONS, not proof that any agent is already executing.  
**Repository:** `Tomex777/Build` · **App:** `artist-scene-studio/` (`studio.artistscene.app`, minSdk 26).  
**Canonical integration:** `integrate/mise-studio-completion-20261020` (last reviewed `df2df6a2a2049cf967f008e206b6f06bf97093dc`; always re-check head immediately before editing).  
**Live coordination:** GitHub issue [#440](https://github.com/Tomex777/Build/issues/440). **Release decision: NO-GO** until real API26/API36 install, interaction and visual acceptance.

## Start these THREE existing MISE agent chats together
1. **Agent 3 — character visuals, posing presentation, the two classmates:** [AGENT_03_CHARACTER_VISUALS_40_MIN.md](AGENT_03_CHARACTER_VISUALS_40_MIN.md).
2. **Agent 4 — real VRM rig/hair/fabric motion and animation:** [AGENT_04_RIG_PHYSICS_40_MIN.md](AGENT_04_RIG_PHYSICS_40_MIN.md).
3. **Agent 10 — independent Android testing, screenshots, critique:** [AGENT_10_INDEPENDENT_QA_40_MIN.md](AGENT_10_INDEPENDENT_QA_40_MIN.md).

Each gets **one separate, simultaneous, 40:00-minute active work session**. These Markdown instructions do *not* start agents or keep chats working in the background; the actual agents must be invoked. The previously assigned ten-agent project structure still exists, but only A3, A4, A10 are assigned by THIS sprint. **Agent 1 stays coordinator/integration owner**, not a fourth 40-minute worker.

## Time-box enforcement — no less, no more
- Start a monotonic **40:00 stopwatch per agent when that agent begins actual work**. Record `T0` and wall-clock start UTC in the first claim on issue #440. Work through the full window. At 40:00 stop the sprint, save/commit any viable result and post a precise handoff with end UTC and measured elapsed; never *claim* a full 40 minutes without measurement.
- Do NOT finish after 7 minutes because code compiles, a PR was opened, or tests were queued. **As soon as a task is implemented, run a visual/device test and inspect real frames**. If done early, immediately pick the next *non-conflicting* existing P0/P1 defect from `docs/agent-backlog.md` and do useful work until the 40:00 boundary; do not stop at a status report.
- Suggested time partitions add to exactly 40 minutes in each agent's file; times are engineering guides, not permission to fabricate a timer or override an agent's tool timeout. If a session/tool is cut short, write **PARTIAL — actual minutes only** and the resume point, never an invented 40-minute completion.
- At T+00 claim files; at T+20 record implementation/evidence; at T+40 publish handoff. Do not idle while CI is queued: inspect source, reproduce an issue with available artifacts, add targeted tests or repair an unrelated owned defect. No artificial waiting for another agent.

## Canonical facts that must not be reset or misreported
- Existing advanced MISE work and PRs #443–#458 were previously integrated into the canonical branch. Don't create a new standalone preview, app, repository, replacement 2D character, or detached development wave.
- Existing **PR #504**, `fix/mise-hair-physics-rig-20261020`, is the *current unmerged hair-motion branch*; last known head `fde6376c2f840f1c0edaaff4031d6f01a7f8116f`. Agent 4 should continue it, check current merge conflicts and tests.
- At last run check: integrated Android CI [39828822136](https://github.com/Tomex777/Build/actions/runs/39828822136) was queued; PR #504 Android CI [39827886295](https://github.com/Tomex777/Build/actions/runs/39827886295) was queued; PR #504 under-tree QA [39827886332](https://github.com/Tomex777/Build/actions/runs/39827886332) was **successful**. These are time-specific, NOT blanket proof of final Android functionality.
- Reproduced earlier defects: female VRM hair showed **zero changed head pixels** after wind/gravity clicks; head roots exist, so genuine frame deformation must be proved. API36 reference PNG export was obscured by Android immersive notice. API26 saved project test selected the wrong pre-seeded scene. ARM64 release staging unexpectedly included non-ARM64 native libraries. Patches for the latter three live on integration but still need fresh confirmation.
- Independent QA previously identified occluded face/hair, stiff posing, toy-like models, tiny centered figure and excess sky. Do not mark visual acceptance merely because something renders.
- Existing MISE rules `AGENTS.md`, `docs/integration-resume.md`, `docs/definition-of-done.md` and ABR all apply: custom styled inputs, no redundant controls/filler, correct icons, full screen, usable controls and visuals on modest Android phones.

## Ownership and conflict policy
| Owner | Writes | Must NOT silently edit |
| --- | --- | --- |
| A3 | `CharacterDemoStyling.kt`, `UnderTreeScene.kt`, tightly scoped scene-presentation/character tests | `VrmSpringRuntime.kt`, `SceneViewport.kt`, `AnimePoseStudyInstrumentedTest.kt`, Gradle/workflows, broad `StudioScreen.kt` |
| A4 | Existing #504: `VrmSpringRuntime.kt`, minimal coordinated `SceneViewport.kt`, `AnimePoseStudyInstrumentedTest.kt` | A3's appearance/under-tree files; release workflows; QA verdict |
| A10 | Independent screenshots/report/evidence and `docs/qa-acceptance-matrix.md`; isolated A10 verification scripts **only if needed** | A3/A4 implementation, their instrumented hair test, coordinator CI and release files |

File paths above are relative to `artist-scene-studio/app/src/main/java/studio/artistscene/app/` unless noted. All shared-source edits require a claim comment on issue #440 **before** making them. A7 normally owns renderer; A4 must coordinate the `SceneViewport.kt` editing window. Agent 1 alone merges PRs to canonical integration and later may coordinate a final device run. Agent 10 must remain independent of A3/A4 implementation.

## Required outputs at T+40
Each of THREE agents reports **actual** start/end/elapsed, files changed, branch/commit/PR, what worked, exact tested API/device, APK installed?, touch/visual screenshot or existing artifact IDs, remaining failures and next executable action. Mark checks **PASS / FAIL / NOT RUN / BLOCKED**, separately. A screenshot must be a real emulator/device frame with source run+SHA. QA signing != production signing. **Do not merge to `main`, publish or pronounce DONE.**
