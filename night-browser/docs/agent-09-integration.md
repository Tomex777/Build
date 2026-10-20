# Agent 9 — integration contract, safety and QA

Branch: `agent/browser-09-peek-site-studio`. Scope is new files only; **do not merge blindly until Agent 1/2/3/4 arbitrate shared ownership**. This PR deliberately avoids modifying `MainActivity.kt`, `BrowserController.kt`, `JourneyStore.kt`, Gradle, and the manifest while other agents work.

## Implemented in this branch

- `PeekOverlay.kt`: isolated disposable Peek view with live URL and title, preview-local Back, progress, close-on-dismiss, and open-current-preview-URL callback. Unlike the old `PeekDialog`, opening a link reached *inside* Peek will not mistakenly open the original link.
- `SiteStudioStore.kt`: durable private-app SharedPreferences rules keyed by exact normalized HTTP(S) scheme, host and port; not wildcard/suffix. Both CSS and JavaScript are off by default. Per-site delete/reset and bounded data sizes.
- `SiteStudioBridge.kt` + `assets/site_studio`: GeckoView built-in WebExtension, top-level sender verification, session allowlist and origin-checked native messaging. No scripts are injected without explicit enabled rules. Temporary previews are scoped to one GeckoSession and never persisted.
- `SiteStudioEditor.kt`: custom Compose code editors, per-origin CSS/JS switches, explicit Javascript risk acknowledgement, preview/save/reset hooks.
- `AdaptiveBrowserMemory.kt`: conservative memory-pressure bitmap trimming; never discards real Gecko tab history/session state.
- `SiteOriginsTest.kt`: origin normalization, ports, scheme/subdomain isolation and unsafe schemes/credentials.

## Exact integration points — Agent 1/2/4

1. **Gecko engine owner (Agent 4):** In `BrowserController`, instantiate
   `val siteStudioStore = SiteStudioStore(appContext)` and
   `val siteStudio = SiteStudioBridge(runtime, siteStudioStore)` once. Immediately before a new GeckoSession is navigated call `siteStudio.attach(session)`. On tab close call `siteStudio.detach(tab.session)` before close. Do not attach to incognito sessions unless a deliberately separate, nonpersistent rule policy is implemented.
2. **UI owner (Agent 1/2):** Replace the old `PeekDialog` call with
   `PeekOverlay(browser, url, onDismiss = { peekUrl = null }, onOpen = { finalUrl -> browser.newTab(finalUrl); peekUrl = null; page = Page.WEB })`.
   Eliminate old PeekDialog after import extraction; do **not** keep duplicate previews.
3. **UI owner (Agent 1/2):** Add one `Site Studio` entry to the existing More menu, enabled only for HTTP(S) pages. Open `SiteStudioEditor(current.url, browser.siteStudio.editorRule(current.session, current.url), ...)`.
   - Preview: `siteStudio.preview(current.session, draft)`; dismiss editor; reload **this** tab.
   - Save: `siteStudioStore.save(rule)`; `siteStudio.discardPreview(current.session)`; dismiss editor; reload this tab. Show failure if save returns false.
   - Reset: `siteStudioStore.remove(current.url)`; discard preview; close editor; reload tab.
   - Undo: pass `canUndo = siteStudioStore.canUndo(current.url)`; call `siteStudioStore.undo(current.url)`, discard the preview and reload; this restores the previous stored rule or clears the most recent newly created rule.
   - Cancel/discard preview: discard preview; reload the tab to remove temporary CSS/JS. **JS side effects cannot be undone while the document is open**, so reload is mandatory.
4. **Agent 3 memory owner:** Register `AdaptiveBrowserMemory({ browser.tabs.toList() }, { browser.activeId })` as an Android ComponentCallbacks2 with application/activity lifecycle, and unregister when destroyed. This clears memory-only thumbnails without discarding session state. Never persist previews from incognito.

## Security notes

The extension ships with HTTP(S) top-level content scripts. It does not log or upload history, page text, code or credentials. The native bridge trusts `WebExtension.MessageSender.url` and session identity, **not** a page-supplied URL. Rules only match exact origins. CSS uses a scoped injected style tag. User-enabled JS is inserted into the page only when the site's own CSP permits inline scripts; it is not an isolation sandbox. Warn user because JS can read page data and perform requests. Do not disable CSP or expand extension privileges to force scripts onto strict sites.

Rules are stored in app-private storage; the app manifest disables backup. A temporary preview draft exists only in RAM and never becomes a saved rule. Changing/deleting JavaScript requires a page reload to remove old effects.

## Tests required after wiring

- Dismiss and promote 20 Peeks, including a page navigated inside Peek, verify original tab URL and back history never change.
- CSS enabled for `https://example.com`: confirm it does not affect `http://example.com`, `https://sub.example.com`, another HTTPS host, or nonstandard port. Reopen after app restart; reset and confirm original page.
- JS toggle off by default; consent check; malicious untrusted code not auto-enabled; verify site-CSP blocked case shows an honest limitation rather than bypass.
- Preview CSS is visible only on the active session and disappears on discard/reload, including after process death.
- Open 10+ tabs, trigger Android trim memory, verify thumbnails can disappear but tab content, navigation history and scripts remain correct.
- Test Android API 26 and API 36 with screenshots, memory logs, real interaction and app reinstall. **This branch's CI is compilation/unit tests only; no claim of runtime/device verification until tested.**

## Status (2026-10-20)

**Implemented:** modules and tests above. **Wired/integrated:** not yet; shared file ownership belongs to Agent 1/2/4. **Built/installed/interaction-tested:** awaiting dedicated CI and coordinator emulator. **Known limitations:** inline user JS may be blocked by website CSP; Peek tab promotion reopens its current URL instead of transferring live form/scroll state. Do not present either capability as complete parity before end-to-end verification.
