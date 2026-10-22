# Annie Browser: Omni Browser upstream import (Agent 1)

> **Source lineage:** https://github.com/REBEL-ROOT/omni-browser

Pinned upstream Git SHA: `9de84cc0d4c7cce32c7052e6d7a8ceab4bdc6d16` (reviewed 2026-10-22). Original project: **Omni Browser**, copyright (C) 2026 RebelRoot Ltd. GPL-3.0-or-later; upstream `TRADEMARK.md` also demands rebranding and visible in-app credit. Copies of `LICENSE` and `TRADEMARK.md` are retained next to this file. This is a **partial code port**; do not release a derivative APK as complete or compliant until the entire combined-work license and notices/complete corresponding source are handled.

## Provenance (path in pinned upstream → modified path in Annie)
- `app/src/main/java/com/rebelroot/omni/browser/session/OmniSessionState.kt` (`d001a5d302fad6d6450f44aa1c9c4b3ba063b33e`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/AnnieSessionState.kt`
- `app/src/main/java/com/rebelroot/omni/browser/session/SessionStatePersistence.kt` (`cfa78760c354ee5f2ee1e12198b69ac369ab4acc`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/SessionStatePersistence.kt`
- `app/src/main/java/com/rebelroot/omni/browser/extensions/BuiltInExtensionManager.kt` (`1ea973d17876d8676939cef84877d29c9ad1cd7e`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/extensions/BuiltInExtensionManager.kt`
- `app/src/main/assets/web_extensions/universal_copy/manifest.json` (`5332972692f087fd324e5af9e151a757fc06d640`) → `annie-android/app/src/main/assets/web_extensions/annie_universal_copy/manifest.json`
- `app/src/main/assets/web_extensions/universal_copy/override.js` (`9cf39e24992462033e65b73c46accfa0dfbb2a77`) → `annie-android/app/src/main/assets/web_extensions/annie_universal_copy/override.js`
- `app/src/main/assets/web_extensions/universal_copy/override.css` (`8c4fd0e515ffcbcafde788721f440520d52ea30e`) → `annie-android/app/src/main/assets/web_extensions/annie_universal_copy/override.css`
- `app/src/main/java/com/rebelroot/omni/browser/AdaptiveBrowserChrome.kt` (pinned upstream version; selected URL input focus/selection behaviour) → modified logic inside `annie-android/app/src/main/java/com/tomex777/annie/AnnieBrowserChrome.kt`; attribution and pinned SHA are in that source header.

- `app/src/main/java/com/rebelroot/omni/browser/useragent/UserAgentManager.kt` (`f3d1f7e29fd38af97e21d4c9d55d50b1b2826fdc`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/useragent/UserAgentManager.kt`
- `app/src/main/java/com/rebelroot/omni/browser/tabs/SmartTabManager.kt` (`5a0cf59f9c9dca26a86795ca928cd9b11460fd1b`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/tabs/SmartTabManager.kt`
- `app/src/main/java/com/rebelroot/omni/browser/BrowserViewModel_FindInPage.kt` (ref: upstream pinned SHA) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/AnnieBrowserFindController.kt` (adapted from ViewModel extension functions into engine state controller)

**Adaptations:** source packages relocated into Annie's internal engine to avoid shipping upstream trademarks as an Android package ID. `OmniSessionState` renamed `AnnieSessionState`; state format preserved. A cancellation guard stops closed-tab debounce jobs from resurrecting removed state. A logged URL was removed. The built-in extension's manifest/name/ID and JS diagnostic label are Annie-branded. Upstream copyright headers remain on original Kotlin sources. No upstream logos are imported. The JS/CSS and manifest derive from the upstream `universal_copy` extension.

`AnnieBrowserEngineBridge` is Annie-native glue, not a replacement browser. It exposes Gecko SessionState recovery/checkpoint and optional Universal Copy WebExtension control to the existing Annie GeckoRuntime (never to script/automation WebView profiles). The extension is **OFF by default** and only installed when a real user-facing control calls the bridge. Its script intercepts page input events, so it should be opt-in and disabled for normal browsing. Agent 2 owns this UI.

## License and security gate

- The upstream README and trademark policy require an original-repository link and visible in-app About/Settings credit (“Based on Omni Browser by RebelRoot …”). Add/verify prior to any distribution. Never claim association or endorsement.
- The combined application may need to be distributed under GPLv3 with corresponding complete source and third-party license notices if conveyed. **Personal-only use is not a waiver.** Any incompatible closed-source licensing must be resolved before public APK release.
- Existing Annie GeckoView: `151.0.20260608154138`; pinned upstream: `154.0.20260824154132`. Inspect API compatibility in real Gradle/CI, not by assumption. Existing Annie `applicationId=com.tomex777.annie`, Android 8/minSdk26, ARM64 unchanged. Do not mix incompatible simultaneous Gecko engine versions.
- Upstream AndroidManifest declares capabilities outside Annie's current permissions (VPN/Tor/network/media, foreground services, notifications, camera). No wholesale permission merge. Review each by feature and least privilege.
- Upstream third-party libraries cover GeckoView, SQLCipher/Room, ZXing, WorkManager, Tor, WireGuard, jlibtorrent, and optional model/media/translation integrations; don't add a huge dependency graph unreviewed.
- Private browser session bytes and URLs must never enter the durable JSON store; guard remains at bridge and persistence layers. Session crash/rotation, concurrent writes, stale IDs, and URL restoration need API26/API36 instrumented tests.
- Annie's About screen now gives the required visible, clickable RebelRoot upstream credit and the imported GPLv3 text is bundled in the APK; root README also links to upstream. End-to-end corresponding-source obligations, full upstream browser features, and API26/API36 emulator acceptance are **not** complete or claimed.

## Engine ↔ UI contract for Agent 2

Instantiate `AnnieBrowserEngineBridge(context, AnnieGeckoRuntime.get(context))` inside Annie's normal standalone Gecko browser model. For normal tabs use stable UUIDs in existing `annie_gecko_tabs_v1` metadata, then `restoreNormalTab(id)` on startup; call `checkpoint(...)` from `ProgressDelegate.onSessionStateChange`; call `forgetNormalTab(id)` on close. **Never call restore/checkpoint for private tabs.** `setUniversalCopy(enabled)` is an opt-in page tool action. Keep one shared GeckoRuntime and existing Gecko secure download and file picker logic.

## Feature inventory (upstream capabilities vs this port)

| Upstream feature | Current Agent 1 status |
|---|---|
| GeckoView tabs/navigation and normal/private modes | Annie baseline retained, full upstream ViewModel **not** ported |
| Durable Gecko session-state serialization | Actual upstream classes ported; behavioral integration and tests ongoing |
| Built-in WebExtension management / Universal Copy | Upstream manager + scripts imported; actual optional control bound in combined candidate, default OFF and hidden on private tabs; device proof pending |
| Bookmarks/history/search/downloads | Annie baseline; upstream implementations **not** ported |
| Third-party WebExtensions, ad/tracker blockers, privacy controls | Not ported / not validated |
| Media download/grabber, player, PDF, QR, translator | Not ported / not validated |
| Vault/SQLCipher, local models/AI, Tor/VPN, sync | Not ported; dependency, entitlement, privacy and security audits required |
| Responsive full Omni browser UI | Annie-native compact Compose chrome now wired to Gecko in combined candidate; only the original focus/selection behaviour adapted from Omni; FULL upstream UI still not ported |

## Acceptance remaining

Gradle compilation, GeckoView API smoke, source test, extension toggle behavior, page load/back/forward, private data isolation, session restore after OS process death, API26/API36 emulators, screenshots, clean APK install/reinstall and complete corresponding-source obligations. **Do not mark ready before verification.**

## Combined integration (Agent 1 + Agent 2)

Draft PR #598 wires actual upstream Gecko services to Annie's native browser UI and adds genuine blank new-tab semantics. The modified UI has a source header crediting the Omni AdaptiveBrowserChrome.kt behaviour. A functional subset is not the complete upstream Omni application; extended features, device testing, source-offer/notice audit and release approval are outstanding.
