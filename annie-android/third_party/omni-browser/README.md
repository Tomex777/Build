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

- `app/src/main/java/com/rebelroot/omni/browser/useragent/UserAgentManager.kt` (`f3d1f7e29fd38af97e21d4c9d55d50b1b2826fdc`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/useragent/UserAgentManager.kt`

- `app/src/main/java/com/rebelroot/omni/browser/tabs/SmartTabManager.kt` (`5a0cf59f9c9dca26a86795ca928cd9b11460fd1b`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/tabs/SmartTabManager.kt`

- `app/src/main/java/com/rebelroot/omni/browser/PermissionPrompts.kt` (`22b9c21d25f054d9454d039a29fdcccc532ace4a`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/permissions/PermissionPrompts.kt`
- `app/src/main/java/com/rebelroot/omni/browser/SitePermission.kt` (`5710180897c41cd9dd550e8511b1d5a3223ba900`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/permissions/SitePermission.kt`
- `app/src/main/java/com/rebelroot/omni/browser/BrowserViewModel_FindInPage.kt` (ref: upstream pinned SHA) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/AnnieBrowserFindController.kt` (adapted from ViewModel extension functions into engine state controller)

- `app/src/main/java/com/rebelroot/omni/bookmarks/model/OmniBookmark.kt` (`7a79cd236aaffcf0495d29b9831bcff798f1aaef`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/bookmarks/model/OmniBookmark.kt`
- `app/src/main/java/com/rebelroot/omni/bookmarks/model/BookmarkNode.kt` (`ded234c972f92d54eac98455572941f32a3fb9ff`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/bookmarks/model/BookmarkNode.kt`
- `app/src/main/java/com/rebelroot/omni/bookmarks/model/BookmarkCollection.kt` (`9dc589e47ca4178be3e193251722927a059b57a9`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/bookmarks/model/BookmarkCollection.kt`
- `app/src/main/java/com/rebelroot/omni/bookmarks/model/BookmarkTreeQueries.kt` (`849d095a6fea6c9651569113ad696c019481d977`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/bookmarks/model/BookmarkTreeQueries.kt`
- `app/src/main/java/com/rebelroot/omni/bookmarks/storage/BookmarkStorage.kt` (`14c95d4f2f58bff559229f23d10ee8925b12f8d4`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/bookmarks/storage/BookmarkStorage.kt`
- `app/src/main/java/com/rebelroot/omni/bookmarks/parser/NetscapeBookmarkParser.kt` (`a973c261af8423064c2e6aff15fd8afe4ac412f5`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/bookmarks/parser/NetscapeBookmarkParser.kt`
- `app/src/main/java/com/rebelroot/omni/bookmarks/export/NetscapeBookmarkExporter.kt` (`8755f4cade312970fcabbe9481261bb1e8576511`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/bookmarks/export/NetscapeBookmarkExporter.kt`

**Bookmark port status:** Real Omni tree CRUD, persistence and Netscape HTML importer/exporter are imported. The HTML codec now has stricter HTTP(S)-only bookmark validation, and storage writes use Android `AtomicFile` (upstream's delete-before-rename could lose existing bookmarks). Existing Annie flat bookmarks retain their original preference-based source of truth; import/export through the real Omni codec is implemented and idempotent, but **folder UI and full canonical migration are not complete**. Imported folders are currently flattened explicitly. Do not claim Omni bookmark parity until Agent 2 supplies folder UX, migration and duplicate/large-library visual tests.

- `app/src/main/java/com/rebelroot/omni/browser/IntentUrlParser.kt` (`c45070865fbe519a7eee5fa81f32f2ffd4a9ac4e`) → `annie-android/app/src/main/java/com/tomex777/annie/browser/engine/intents/IntentUrlParser.kt` (strict HTTP/HTTPS-only incoming intents; no upstream extension shortcut trust, no sensitive URL logging)

**New Annie adapters:** `permissions/AnnieBrowserPermissionBroker.kt` (foreground-only consent, origin-scoped remembered choices, no silent system permission grants) and `AnnieBrowserEngineBridge.kt` (shared GeckoRuntime). These are new Annie integration code rather than unmodified upstream files.

**Adaptations:** source packages relocated into Annie's internal engine to avoid shipping upstream trademarks as an Android package ID. `OmniSessionState` renamed `AnnieSessionState`; state format preserved. A cancellation guard stops closed-tab debounce jobs from resurrecting removed state. A logged URL was removed. Omni's SmartTabManager now matches canonical hostnames (never substring-lookalike domains) and exposes semantic vector icon keys rather than Emoji. Only non-private tabs are available to Annie's grouped-normal-tabs API. Omni's upstream pattern of lazy session restoration is adapted in the existing Gecko browser model, keeping inactive saved tabs unopen at cold start. The upstream user-agent preference manager is relocated, uses a new Annie preference namespace, and fixes overly broad parent-domain rule matching. Its preset strings are retained as upstream historical versions, never silently advertised as current. The built-in extension's manifest/name/ID and JS diagnostic label are Annie-branded. Upstream copyright headers remain on original Kotlin sources. No upstream logos are imported. Source permission prompts, site-permission schema and find-page behavior retain attribution; no OS permission was silently added to the manifest. The JS/CSS and manifest derive from the upstream `universal_copy` extension.

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
| GeckoView tabs/navigation and normal/private modes | Real Omni lazy restoration and active/inactive Gecko session management added; native unloaded blank Home matches Agent 2 contract; full upstream ViewModel **not** ported |
| Durable Gecko session-state serialization | Actual upstream classes ported; behavioral integration and tests ongoing |
| Built-in WebExtension management / Universal Copy | Actual upstream manager + scripts imported; explicit opt-in hook pending UI |
| Bookmarks/history/search/downloads | Omni canonical bookmark tree and HTML parser/exporter imported; flat Annie bookmarks use new codec; upstream history/download managers not ported |
| External share, view and web-search intents | Imported upstream parser; incoming HTTP(S) links/search text open inside Annie Gecko, reject dangerous schemes/unsafe redirects; native Android manifest exposure not expanded |
| Omni Gecko find-in-page | Upstream find/clear/next/previous logic adapted to an engine controller, hooked into the browser model; Agent 2's visual find bar is pending |
| Omni content, media and Android permission prompts | Original prompt models imported and connected through consent-gated Gecko delegate; without Agent 2's foreground UX handlers all requests deny, not hang |
| Omni smart tab grouping (normal tabs only) | Imported upstream categorizer with exact-host/subdomain security fixes and a stable UI API; tab sheet UI is Agent 2 | 
| Upstream per-site/global User-Agent preferences | Original upstream manager ported and wired to Gecko session settings; Agent 2 user controls pending | 
| Third-party WebExtensions, ad/tracker blockers, privacy controls | Not ported / not validated |
| Media download/grabber, player, PDF, QR, translator | Not ported / not validated |
| Vault/SQLCipher, local models/AI, Tor/VPN, sync | Not ported; dependency, entitlement, privacy and security audits required |
| Responsive full Omni browser UI | Agent 2 responsibility; not ported yet |

## Acceptance remaining

Gradle compilation, GeckoView API smoke, source test, extension toggle behavior, page load/back/forward, private data isolation, session restore after OS process death, API26/API36 emulators, screenshots, clean APK install/reinstall and complete corresponding-source obligations. **Do not mark ready before verification.**
