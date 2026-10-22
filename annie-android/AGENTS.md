# Annie native development agent instructions

The **canonical current recovery briefing** is [docs/ANNIE_RECOVERY_FIVE_AGENTS_2026-10-22.md](docs/ANNIE_RECOVERY_FIVE_AGENTS_2026-10-22.md). Read it and your assigned [agent workstream](docs/agents/) before making changes.

**Every agent:** this is the real Android app under `annie-android/`, not the unrelated web prototype. Obey the user's strict ABR/EBR standard (the repository formalizes ABR at `ci/check-abr.py`), minSdk 26, ARM64, no duplicate controls, native-styled input, no filler copy, proper vector icons, fullscreen system-bar behavior, screenshot inspections and actual emulator functional acceptance. Do not call work complete just because it compiles. Preserve permanent package ID / user data. Do not work directly on someone else's owned file without PR coordination.

**The five workstreams** (no automatic agent launch implied):

1. [Omni source import + licensing](docs/agents/01-OMNI-PORT.md)
2. [Annie Browser UX & integration](docs/agents/02-BROWSER-UX.md)
3. [Script Studio file-driven editor](docs/agents/03-STUDIO.md)
4. [Canvas host lifecycle and resource controls](docs/agents/04-CANVAS-LIFECYCLE.md)
5. [Chat/voice/visual quality + release gates](docs/agents/05-ABR-QUALITY.md)

The user specifically wants **actual upstream Omni Browser** rebranded as Annie Browser, not an Omni-inspired replacement; handle GPLv3/attribution/trademark and distribution obligations. The current recovery branch has an *initial untested* Studio fix; it is not a validated APK. Maintain an honest status report with API26/API36 emulator screenshot and tests.
