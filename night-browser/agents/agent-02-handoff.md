# NIGHT Browser — Agent 02 implementation handoff

**Task:** B-002 — Home, omnibar, navigation UX.
**Owner:** Agent 2. **Source branch:** `agent/browser-02-home`.
**Base / PR target:** `feature/night-browser-multi-agent-20261020`.
**Owns:** `NightStartPage.kt`, `StartSiteStore.kt`, `StartSiteUrlsTest.kt`, agent-only CI, this note.
**Avoids:** MainActivity, BrowserController, JourneyStore, tabs UI, manifest and Gradle (claimed by other agents).

## Code delivered

- Responsive, width-bounded, four-column handset / six-column large-display native Compose Home; small NIGHT header, purpose-built address/search field with IME Go, explicit Paste tap, submit arrow, and keyboard dismissal.
- User-addable, editable and removable speed dials, persisted locally across launches. The screen never simply replaces user selections with recently visited websites; user deletion of defaults persists.
- Shortcut URLs validated for http(s), non-empty host, no credentials, no embedded whitespace, no javascript/file/data/ftp schemes, 16-site maximum and duplicate prevention.
- Same-origin HTTPS-only website favicons with no third-party favicon service. Limits response to 180 KB, image dimensions to 512px and in-memory cache to 2 MB; falls back to an initial where icons are missing or non-decodable.
- Recent visited entries come from Journey nodes already on-device, plus the latest non-empty Journey card. No AI dependency, history uploads or Omni GPL code/assets.
- JUnit regression cases for shortcut URL normalization and unsafe inputs.
- Dedicated Agent 2 GitHub Actions Android build/test gate, scoped to this branch and integration-target PR.

## Wiring needed by Agent 1 (shared-file ownership)

Replace the `Page.HOME` case of `NightBrowser` in `MainActivity.kt`:

```kotlin
Page.HOME -> NightStartPage(
    browser = browser,
    onOpen = { raw -> browser.navigate(raw); page = Page.WEB },
    onJourneys = { page = Page.JOURNEYS }
)
```

On HOME, hide the existing bottom address row so there is **one** address/search entry, not two. Keep the bottom navigation (back/forward/tabs/menu), and preserve system-navigation inset padding. Remove the old unused `HomePanel` only after the new one is integrated and reviewed. Agent 1 owns this merge/hook because other specialists are also editing MainActivity.

## Verification ledger

- **Code written and committed:** yes.
- **Unit tests / APK build:** PASS on GitHub Actions `NIGHT Browser Agent 2 — Home Compile Gate`, push run [39358298115](https://github.com/Tomex777/Build/actions/runs/39358298115), at commit `bbee892330cf68cf37ec39bd96bc32d9c2b6e2a7` (the Home favicon-decoder source revision). `:app:testDebugUnitTest` and `:app:assembleDebug` both succeeded. `night-browser-agent02-arm64` debug APK artifact uploaded successfully. The separate PR-event run may remain queued.
- **Emulator installation:** not yet run.
- **Physical-device installation:** not yet run.
- **Real interactions / Android screenshots:** not yet run because coordinator must wire the new Home composable. Agent 10 / Agent 1 should test both 320 and 390 dp, tablet 840+ dp, rotations and keyboard/insets.
- **Not integrated into MainActivity:** intentionally; shared ownership protocol.
- **No claim of production readiness.**

## Live QA script after integration

1. Launch with first tab: Home title fits, single omnibar, speed dials and + tile visible at 320 and 390 dp.
2. Enter `example.com` via keyboard Go; verify HTTPS Gecko navigation. Enter `night browser search`; verify search, keyboard dismissal and re-enter.
3. Copy an address to clipboard; tap Paste and Go. Verify URL and query behavior.
4. Add site (`example.org`), rename it, open it, then remove it; kill/relaunch browser to verify persistence. Remove all defaults to validate honest empty state.
5. Try `javascript:alert(1)`, `file:///x`, duplicate URL and credential-bearing URLs; ensure no shortcut is saved.
6. Visit real sites, return Home, tap recent item and Journeys card. Check back/forward, tab grid and insets.
7. Switch 320, 390, 840 dp widths and landscape; inspect real screenshots. Verify no duplicate omnibar.
8. Verify no third-party favicon service, unexpected external data transfer or background History sync.

## Next action

Agent 1: review and integrate the small MainActivity hook above, then ask Agent 10 for actual installed Android UX verification. CI build/unit gate is green; Agent 1 now wires and reviews Home, Agent 10 installs/interaction-tests. Any UI or compilation regression found after merging must be fixed and retested.
