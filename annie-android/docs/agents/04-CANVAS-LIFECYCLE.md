# Agent 4 — Canvas host-level Pause, Resume and Close

**Mission:** Preserve existing interactive Canvas/Snake visuals and swipe behavior (user specifically praised them), but give control over whether the **Canvas runtime itself** consumes CPU/GPU in a chat message.

## Exact distinction

- **Game Pause** is an action inside Snake (or another app) and only affects its own gameplay.
- **Canvas Pause** is a host action that suspends JavaScript animation/host timers and media in the **entire isolated Canvas message**. Snake keeps its board/game state where possible. Resume continues its state.
- **Canvas Close** explicitly tears down the Canvas WebView/JS/audio and releases memory, leaving a small restart/open card in the chat history. Restarting a closed Canvas may reset ephemeral game state unless game-defined persistence exists.
- Automatic pause on app background, chat navigated away, message out of render scope, or fullscreen overlay exit if no inline host is visible. No uncontrolled offscreen loops.

## Implementation guidance

- Review `AnnieCanvasMessage.kt`, message controller ownership, fullscreen handoff and WebView lifecycle. Expose one compact Pause/Resume toggle + Close action by the current Expand control (or in same overflow if space constrained); maintain consistent API on inline and fullscreen surfaces. Do not clutter the card with duplicate controls or use text-only “X” as a fake icon.
- Inject scoped control APIs into each document *before* user JS executes so arbitrary `requestAnimationFrame`, intervals/timeouts and CSS animations can be paused cooperatively; mute/pause media, handle visibility events and re-entrancy, handle race when close competes with expand. `evaluateJavascript` on the exact view is okay when controlled; **avoid exporting privileged JS interfaces into untrusted Canvas**.
- **Do not use `WebView.pauseTimers()` as a per-message pause mechanism**: its timer suspension can affect other WebViews globally, including chat/other Canvas previews. Prove paused Canvas does not stall independent browser/other messages. Lifecycle can call WebView `onPause`/ `onResume` only after checking its semantics and isolated state behavior.
- Ensure `destroy()` occurs on UI thread, after detaching from parent and canceling coroutines/observers, with no leaks/scroll injection. Pause Close should not delete the chat message or change Canvas document policy (offline, sandboxed, no browser cookies/network).
- User should choose Close without accidentally closing Annie or navigating away; re-open remains available in same message. Touch/swipes need to continue only inside active Canvas.

## Test matrix

1. Instrument test frame/tick counter with two independent Canvas messages and a Gecko tab.
2. Visible tick rate rises; host Pause freezes or near-zeroes it without Snake-specific hooks; Resume advances without resetting state; expanding while paused stays paused.
3. Offscreen/app-background pauses; reopen restores state if WebView retained; Close destroys and frees resources (track count/heap and audio), restart creates fresh view.
4. Inline→fullscreen→inline preserves state when not closed; Android Back and orientation do not start double loops.
5. API 26 and API 36 real finger Snake swipe and fullscreen test still pass; measure smooth frame timing and background CPU.

**Evidence:** device log + screenshots showing Pause and Close + diff and tests. A pretty button that does not stop JavaScript does NOT pass.
