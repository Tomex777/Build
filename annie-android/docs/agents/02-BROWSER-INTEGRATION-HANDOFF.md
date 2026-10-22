# Annie Browser — Agent 2 integration handoff (22 October 2026)

Branch: `annie-agent2-browser-ux-20261022`; issue: #590. Targets the coordinated `annie-five-agent-recovery-20261022` branch. This document describes **real native Compose code**, not a preview or claim of an integrated Omni APK.

## Implemented in Agent 2 scope

- `AnnieBrowserChrome.kt` is isolated from engine files so Agent 1 can port actual Omni source without being overwritten.
- Compact 48dp omnibox with IME Go **and one in-field Go button**, rather than the previous giant address field and separate button.
- A tab-count button and responsive two-/three-/four-column tab switcher; each tab has a distinct close action.
- Native bookmark/history lists populated **only by the model's actual saved records**, real download panel composition, private tab action, desktop toggle, sharing, Gecko content slot, loading progress, focus handling, Android/system insets and 48dp touch targets.
- Only genuinely un-navigated tabs with an empty URL or `about:blank` display the Annie Browser home state. No fake website tiles, advertising copy, duplicate navigation CTA or mock downloads.
- `AnnieLegacyGeckoBrowserChrome` adapts the existing Gecko model without creating another runtime, recreating history or disturbing script-isolated WebView profiles.
- **Now wired into the real standalone browser entrypoint**: the old Compose tab/address/footer chrome below the existing file-picker lifecycle is replaced with `AnnieLegacyGeckoBrowserChrome(model, onExit)`. Both engine delegates and file chooser ownership are retained. Browser UX is no longer orphaned code.
- Optional opt-in **Universal Copy** menu entry is deliberately hidden until the Omni engine supplies an actual enable/disable callback; it is disabled on private tabs and browser disposal. The adapter accepts an optional callback from Agent 1; it does not pretend the extension works before connection.
- Real UI instrumentation captures Compose-root screenshots of Home and Tabs from device runs; `.github/workflows/annie-agent2-browser-ui.yml` builds and runs API26/API36 at 360dp/393dp/tablet/landscape. Output is **pending**, not fabricated.
- Compose instrumentation `AnnieBrowserChromeUiTest` covers blank start page, actual address dispatch, tab select-versus-close and library state.

## Agent 1 must supply and wire

1. Preserve stable state/action semantics or replace the **adapter** with Omni BrowserViewModel bindings (the chrome itself is engine-independent).
2. **UI tail is already wired in Agent 2 PR**, immediately after the original ActivityResult file-picker registration and `DisposableEffect` cleanup; preserve this UI replacement when merging Agent 1 backend changes. The old browser chrome has been removed. Agent 1 owns model/session internals and must not restore it.
3. **Still outstanding in Agent 1 backend**: create new tabs initially as a true blank/home state, not an automatically loaded `https://www.google.com/`. `AnnieBrowserAddress.resolve()` currently rejects `about:blank`; treat blank as an **internal sentinel** and NEVER allow the user to inject `about:`, `javascript:`, `file:`, etc. through the omnibox. New-tab home must not send a network request.
4. Expose native Gecko media fullscreen and permission prompt hooks from real Omni implementation. Compose chrome must not fake them.
5. Account for orientation/process-death restoration, tab selection/scroll, normal/private persistence and history in the ported state source. Do not persist private URLs.
6. To expose the actual optional upstream Universal Copy extension, pass `universalCopy = { enabled -> model.setUniversalCopy(enabled) }` from the host after Agent 1 engine merges. Until then the menu item stays hidden. Do not enable by default or in private mode.
7. IMPORTANT: upstream Omni GPLv3 source/trademark attribution is Agent 1 ownership; Agent 2's UI component does not provide evidence of the upstream source port.

## Validation that must happen before merging

- Run `python3 ci/check-abr.py` from `annie-android`, `./gradlew :app:testDebugUnitTest :app:assembleDebug`, and on installed API26/API36 Android emulators `./gradlew :app:connectedDebugAndroidTest`. Compare real screenshots at 360dp, 393dp, 600+dp and landscape/split screen.
- Verify fresh home → address Go → page load/redirect → back/forward → tabs/close/restore → bookmark/history → real download → upload → permission prompts → fullscreen video/image → private isolation → background/rotate/process-death.
- Check that the real Gecko page owns scroll/fling, IME doesn't obscure the page, and tabs don't cause unnecessary reloaded sessions. Test >5 tabs for memory/frame stability.
- **Current environment has no Android SDK, emulator, Gradle execution or screenshot evidence. Code is not certified as compiled or installed.** Do not merge on documentation or a green source-only check.

## Agent ownership

Agent 2 must not modify `AnnieGeckoBrowser.kt`, upstream-import directories, Gradle/manifest Gecko declarations or engine state simultaneously with Agent 1. Integration owner should combine the branches deliberately, fix API mismatches and rerun all tests.
