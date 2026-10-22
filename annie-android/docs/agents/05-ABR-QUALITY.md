# Agent 5 — Annie comprehensive ABR/EBR quality, chat/voice, integration QA

**Mission:** This is **not** just QA documentation. Fix mismatched native UI and real behaviors across Annie; gate Agent 1–4 merged work with physical-looking emulator screenshots and actual interaction tests.

## Screenshot-driven analysis

- Persona Sol screen: avatar/name header has large padding; short welcome bubble with massive dark remainder (normal for short chat but avoid awkward vertical design), default composer at bottom with small icons and “Message Annie…” input. Do not arbitrarily replace existing persona identity.
- Persona Iris screen: Canvas card mostly works; snake swipe is a positive regression case. Below, user chat and assistant help/voice bubble show very different text scale and low-contrast metadata; chat/send/voice interactions require end-to-end validation.
- Browser visual defects handled by Agent 2, Studio by Agent 3, Canvas controls by Agent 4. You own cross-screen visual language, chat/voice polish, native intents, accessibility, acceptance tests and regression fixes outside their owned files.

## Native functionality/quality checklist

- Chat: send text, slash commands, autocomplete, personae switching, session restoration after process death, streaming AI vs local commands, permissions and error cases, keyboard focus, message selection, scroll to latest, markdown/rich messages, image attachments and gallery.
- Audio: record, cancel, pause/continue, waveform display, playback, send; no orphan microphone capture while app background; permission-denied guidance and touch targets.
- Images/video: file selection, upload, viewer zoom/pan/fullscreen, downloaded media open/with, media controls, proper isolation of touch/scroll behind fullscreen overlay.
- Visuals: typography hierarchy, card alignment, safe area/status/navigation bars, 48dp controls, thin intentional borders, true vector icons, responsive layouts at narrow 360dp phone, typical 393dp, 600+dp tablet and landscape; no default Android textboxes, duplicate controls, filler slogans.
- Performance: long conversation, multiple Canvas/web pages, navigation churn, background/resume; watch memory leaks, stalled scrolling, input latency, unnecessary recomposition and WebView audio/timer remnants.
- Compliance: `annie-android/ci/check-abr.py`, API 26 minimum, ARM64, unchanged `com.tomex777.annie`, signed upgrade continuity, fullscreen hides **only** correct system bars.

## Integration protocol

- Read each agent PR and prove real changed behaviors. File reproducible issues with screenshots; hand bugfixes to owner rather than overwriting their change without coordination.
- Update screenshot acceptance so “looks better” has specific checks, but never massage thresholds to silence failures.
- Run JVM and all relevant native instrumented tests on API26 and API36, including multi-chat, Studio, Gecko, Canvas swipe, image fullscreen, mic/voice, downloads, release-install and process-death.
- Run production-signed ARM64 install and reinstall on both emulators; if physical Samsung was not personally tested state **not tested**. Report exact workflows + build SHA and screenshot/log paths.
- No completion declaration until UI comparison from four user screenshots, regressions, behavior and source license checks are passed.

## Exit report format

Changed UI/flows; tested devices/API; artifact IDs and screenshots; passed/failed/skipped tests; perf observations; remaining user-visible issues and explicit blockers. “Compiled” and “deployed” are not acceptance results.
