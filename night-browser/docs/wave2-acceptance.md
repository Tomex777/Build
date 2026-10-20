# NIGHT Browser — Wave-2 whole-app acceptance matrix

**Branch:** `feature/night-browser-multi-agent-20261020`. **Issue:** [#460](https://github.com/Tomex777/Build/issues/460). **Coordinator:** Agent 1; **independent judge:** Agent 10. Whole app must build and install on Android minSdk26. Passing disconnected module unit tests does not imply Android usability.

| Gate | Actual user workflow | Test and status |
|---|---|---|
| Core browse | Type URL, search text, redirects, back/forward, reload, login forms | A4/A10: real site interactions on API26 and 36 |
| Home | One address/omnibar, editable quick access and persisted changes, responsive toolbar | A2/A10 screenshots 320dp/390dp/tablet and restart |
| Tabs | 20 real pages, preview screenshots, switch/close/undo, restoration, low memory | A3/A10 screenshot/memory and force-stop |
| Incognito | Sign in separately, verify cookie/history isolation; never store private Journeys or AI data | A3/A10 private-vs-normal regression |
| Files | Real single/multiple upload, image/PDF/large-file authenticated download, open/delete | A4/A6/A10 SAF + actual byte/MIME checks |
| Permissions | Camera/mic/location prompt, allow/deny/revoke, safe external link, TLS errors | A4/A10 Android OS + Gecko permission flows |
| Journeys | A→B→C then back A→D; graph reopen/search/edit/export after restart | A5/A10 branching and redaction tests |
| Library | Real bookmarks/history/reading list/download statuses, search/export/cleanup | A6/A10 local persistence and files |
| Time Machine | Two PDF versions and/or genuine offline capture with truthful label, view offline, export/delete | A7/A10 network-off proof |
| Peek/Site Studio | Long-press link disposable preview/promote; origin CSS/JS edit with undo/no cross-site leak | A9/A10 history and origin regression |
| AI Universal Search | Configured provider real model response, cited sources, consent/no-key/opt-out/timeout and private blocking | A8/A10 network/privacy trace with test key outside code |
| Stability & polish | 20+ real page scenarios, rotation, API26/36, lower-memory device, crash-free touch use | A10 pass/fail with screenshots and logs |

**Artifact report per scenario:** exact integrated SHA, APK name+build, device/API/viewport, action, expected/observed, screenshot/log, pass/fail/unverified, owner, fix PR. All P0 flows must be verified before release. No release signing/main merge without owner approval. UI quality is judged on true device screenshots and interactions, not source presence.
