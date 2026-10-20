# NIGHT Browser — Wave 2 independent Android QA (Agent 10)

This is **executable black-box testing**, not proof the browser is finished. The suite drives the installed APK through Android accessibility + ADB touches while GeckoView loads a synthetic local website. It also attempts an external HTTPS site (`https://example.com`). Android's OS and the HTTP fixture report what happened instead of relying solely on a mocked UI.

- `wave2_fixture.py`: deterministic website (HTTP redirect, 404/500, links, cookies, form POST, upload form, PNG/PDF/BIN download). Listens on host port **8765**, accessible from Android emulator at `10.0.2.2:8765`. No real login, user content, actual credentials, raw cookies, or upload bytes persisted.
- `wave2_blackbox.py`: 30 attempted direct Android/Gecko scenarios (`C01–C30`) and explicit `C31–C39` release gaps. Records screenshot PNGs only of synthetic/public pages, process PSS, app/start SHA and reproducible failures. **NOT_RUN and BLOCKED do not count as PASS.** The scenario names describe each narrow assertion; e.g. a download request alone is explicitly NOT a verified saved/opened download.
- `test_wave2.py`: stdlib unit tests on fixture paths, redirect, cookie yes/no measurements, MIME/bytes, POST receipt and reporting-status integrity.
- `.github/workflows/night-browser-wave2-qa.yml`: APK compilation and API **26 + 36** emulators; uploads real screenshots and structured report artifacts. A green report-only workflow only means the harness executed. **It does not greenlight a release.**

## Reproduce on a clean disposable emulator

Requires Python 3, ADB, the Android SDK, Java 17 and an actual installed `com.night.browser` APK. **The runner invokes `pm clear` and destroys that app's data. Never use a personal device or important emulator profile.**

Terminal A:

```sh
python3 night-browser/qa/wave2_fixture.py
```

Terminal B (emulator booted, from repo root):

```sh
adb install -r night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk
python3 -m unittest discover -s night-browser/qa -p 'test_wave2*.py' -v
python3 night-browser/qa/wave2_blackbox.py --apk night-browser/app/build/outputs/apk/debug/app-x86_64-debug.apk --out night-browser/qa/results/local
```

To enforce release gating with **no** skipped/blocked/failing cases, append `--gate`. This deliberately fails until missing UX/security functionality and hands-on evidence have actually been certified; do not use the report-only job as a release approval. Keep the matrix artifact linked to the **actual integration SHA**, not individual specialist branches.

## Exact evidence emitted

`report.json`: UTC time, current CI git SHA (or `local-unknown`), APK SHA256, API/model/viewport, disposable-profile disclaimer and per-case PASS/FAIL/BLOCKED/NOT_RUN, duration and screenshot file. `report.md`: human audit summary. No raw `logcat` dump, XML hierarchy, browsing history, auth token, or user cookie values are uploaded. Fatal exception lines are filtered to app-process descriptors.

## Integration and known limits

Agent 10 does not edit production Controller, Activities, manifests, Home components or feature stores. This PR targets `feature/night-browser-multi-agent-20261020` and should be reviewed against assembly PR #439 and original QA PR #426 (to avoid redundant workflows). Reviewer A1 owns integrated source and shared controller hooks.

Some tests intentionally fail or report NOT_RUN on the current partially assembled prototype. The fixtures verify Gecko loads and some UI state, **not** security in themselves: private-cookie isolation requires positive same-origin regular-cookie control then absent private-cookie control; form POST needs fixture POST evidence; downloads need saved byte SHA/MIME/open proof; real provider AI requires opt-in and an actual model response; Site Studio requires origin separation; Time Machine needs verified offline open. Physical phone, performance tracing, accessibility screen-reader semantics, signed release and authentic website authentication remain separately required.

Issue #460 coordinates defects. Record `[BROWSER A10][VERIFIED|BLOCKED|HANDOFF]` with integration SHA, API, exact case ID/steps, first-failing screenshot/JSON and owning agent before claiming release readiness.
