# Annie recovery: five independent agent workstreams (2026-10-22)

Status: **implementation backlog + first Studio patch**, **not** a claim that five autonomous agents are running, Omni is integrated, or a tested APK exists.

## Primary objective

Fix the **actual native Annie Android app**, not an HTML/mock browser and not a separate preview. User feedback from four Samsung screenshots: the current Canvas/Snake surface is notably improved, while **browser UX, Script Studio selection, app visual consistency, and Canvas resource control are not acceptable**. A green Gradle build alone is never sufficient.

Canonical base for this recovery PR: `annie-abr-integration-20261020` (the current ABR integration candidate). Preserve `com.tomex777.annie` for upgrades, `minSdk 26`, ARM64 release, existing data and features. Treat `annie-release-1.0.0-20261013` as the integration target *after* the ABR feature line is reconciled and acceptance passes. Do not independently cherry-pick stale branches.

## Screenshot-driven defects (reported on physical Samsung, 22 October)

1. **Script Studio:** Files / Editor / ENV / API is the wrong top-level structure. The first script appears selected on first opening, despite no intentional tap; the user suspects the Editor tab forces this. **Only Files, ENV, API are top-level**. Editor is an inner destination reached by selecting/creating a file. Startup with no `initialProjectId` must have **no** selected file, no phantom editable source, and Preview disabled. Explicit project deep links still work; navigation may not discard unsaved drafts.
2. **Standalone browser:** screenshot shows an oversized omnibox with a separate Go button, poorly balanced top tab strip, tiny bottom glyphs, an undifferentiated gray blank surface, and mismatched spacing/typography/colors compared with Annie. Browser is not expected to be cosmetic tweaks of the current shell; user explicitly requests **Omni Browser itself**, integrated as **Annie Browser**, not an unrelated browser implementation inspired by Omni.
3. **Canvas:** Snake rendering and direct swipe are good, **preserve them**. Need a host-level **Pause/Resume Canvas** control independent of the game’s own restart/pause, and **Close Canvas** to stop resource consumption. Place controls discreetly beside the existing expand affordance in the message header or fullscreen chrome; no extra redundant buttons. Background/offscreen Canvas must not keep running uncontrolled. Preserve message and fullscreen state whenever reasonably possible.
4. **Chat & identity:** the Sol and Iris screens demonstrate disparate header styling, excessive dark empty space, small interface elements and low-weight text; chat input says “Message Annie…” while personas vary. This is a request for comprehensive polish and behavioral testing, not a reason to rename personas or remove scripted capabilities. Review voice recording/send as well as command suggestions and host-AI boundaries.
5. **System UI:** screenshots show visible status bar on non-fullscreen screens, which is expected; hide it only for genuine fullscreen Canvas/image/media views. Recheck gestures, IME insets, landscape, safe areas, responsiveness and accessibility.

## Non-negotiable ABR / EBR contract

The user calls the desired strict execution discipline “EBR” here; the repository already contains the **ABR / App Building Rules** checker at `annie-android/ci/check-abr.py`. Preserve its established requirements; do not invent a new expanded definition for “EBR”. In practice: no two controls doing the same job; no stock Android textboxes; no filler/disclaimers; real vector icons (not emoji/text placeholder controls); Android API 26+ and ARM64; functional and visual completeness; test on API 26 and API 36 emulators with actual screenshot evidence; preserve physical-device requirements separately; hiding status bar in true fullscreen; no regressions to script/runtime, browser profile isolation, media, image viewer, voices, chat persistence.

**Do not claim complete or approved before real emulator install, regression tests, and screenshot review.**

## Agent ownership and integration (five separate tracks)

| Agent | Main file / issue | Exclusive primary scope | Dependency / integration |
| --- | --- | --- | --- |
| 1 | [01-OMNI-PORT.md](agents/01-OMNI-PORT.md) | License-compliant upstream Omni source import; working Gecko engine/services | Agent 2 must integrate UI; neither overwrites existing browser until port is validated |
| 2 | [02-BROWSER-UX.md](agents/02-BROWSER-UX.md) | Browser tabs/toolbar/start-page/navigation/responsive UX and Annie integration | Works against Agent 1 stable interface; owns end-user UI |
| 3 | [03-STUDIO.md](agents/03-STUDIO.md) | Script Studio Files/ENV/API, implicit selection, file-driven editor, draft state | Initial implementation already committed on recovery branch; test and finish |
| 4 | [04-CANVAS-LIFECYCLE.md](agents/04-CANVAS-LIFECYCLE.md) | Canvas host Pause/Resume/Close, WebView lifecycle and perf | Do not change Snake mechanics; isolate from browser timers |
| 5 | [05-ABR-QUALITY.md](agents/05-ABR-QUALITY.md) | Chat/voice/responsiveness/iconography, cross-component acceptance + regressions | Coordinates screenshots/CI, does not silently rewrite others’ logic |

### Coordination

- Each agent works in its **own topic branch and PR**, with isolated ownership. If a change crosses files owned by another agent, communicate the interface as a PR comment instead of racing to edit the same file.
- Work on the native project at `annie-android/`, not the separate web prototype. All relevant screenshot comparisons should use the **real installed Android application**.
- Before any code, read `annie-android/docs/ROADMAP_AUDIT_2026-10-12.md`, `annie-android/ci/check-abr.py`, and relevant actual source; account for `scripts/repair-m0b-merge.py` overlays that run in CI.
- Each PR reports: changed files, before/after screenshots from emulator(s), exact test commands/results, memory/frame measurements if relevant, risks, accessibility, and whether device validation was done.
- Agent 5 has veto on false “passed” claims; final integration requires both API26/API36 instrumentation, owner-signed ARM64 install/reinstall and process-death restoration **plus physical-phone checks** before stating device-ready.
- *Never* equate documented plans, staged pull requests or CI queued status with five active workers or completed implementation.

### First changes in this recovery branch

`ScriptStudio.kt`: remove Editor from the top segmented tab row, retain its dedicated editor screen on file tap, and stop picking the first project by default; `ScriptStudioFileCreationTest.kt`: add opening-without-selection case and migrate old Editor-tab test flows. **Not yet build/device validated.** Agent 3 must inspect, compile, test, and repair anything missed.

## Priority sequence

P0 Agent 3 Studio selection/navigation, Agent 4 host Canvas pause/close, Agent 1 Omni license/source assessment and true-source import foundation. P1 Agent 2 full browser UX + integration, Agent 5 full visual/interaction sweeps. Merge only after individual and combined regression gates. Document absent upstream features rather than pretending they are supported.

## External upstream: Omni Browser

Source: https://github.com/REBEL-ROOT/omni-browser ; upstream is an Android Jetpack Compose + GeckoView project requiring Android 8+ and listing **GPL-3.0** with a project-specific trademark/attribution notice. Review exact `LICENSE`, `TRADEMARK.md` and dependency list at a pinned commit/tag. Calling Annie “personal” does **not** nullify the GPL or attribution terms; if the combined derivative is distributed, investigate and satisfy corresponding-source and notice obligations. Rebrand away from Omni name/logo, preserve upstream copyrights and requested upstream link. Do not silently graft an incompatible license into code intended to remain closed source; flag this as a distribution decision.

## Acceptance matrix

- **Studio:** initial cold open unselected / explicit deep link selected / tap file opens real editor / Files tab returns / ENV and API remain / create, rename, delete, import, ZIP / corrupt storage or autosave failure retains draft / rotate + process-death.
- **Browser:** first new tab useful and styled / keyboard Go + on-screen navigation / forward/back/tabs/history/bookmarks/search/downloads / page focus/scroll preservation / image upload, profile photo, video, extensions where supported / at least 360dp, 393dp, tablet and landscape.
- **Canvas:** count animation frames while visible vs paused vs app background; resume same in-memory state where possible; close releases JS/audio/timers/WebView; no interference with chat, other Canvas messages or Gecko browser; swipe Snake still works on API26 and API36.
- **Quality:** Android statuses/safe areas, polished spacing and touch targets (>=48dp where applicable), actual SVG/vector icons, microphone recording/send/cancel, images/fullscreen no page scroll leakage, chat long-session, dark theme readability, no regressions.
- **Delivery:** smoke screenshots and recording from emulator + green tested installable artifact; no fabricated screenshots.

