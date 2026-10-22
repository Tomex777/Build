# Agent 1 — port actual Omni Browser source into Annie

**Mission:** The user explicitly wants **Omni Browser itself**, not just GeckoView wrapped to resemble it. Import/refactor upstream browser application code into Annie with the app presented publicly as **Annie Browser**; preserve relevant Omni capabilities, not simply a mock. Source: https://github.com/REBEL-ROOT/omni-browser .

## First: mandatory source/license audit

1. Pin a recent reviewed Omni **Git SHA/tag**, record version/date and link, and inspect `LICENSE`, `TRADEMARK.md`, copyright headers, `app/build.gradle.kts`, manifest, native libs, vendored WebExtensions, worker, and app services. Record upstream license and any third-party restrictions for individual assets.
2. Upstream describes GPLv3, including rebranding and attribution/link requirements. Personal use does **not** waive license; distribution of a derivative may obligate GPL source distribution. Maintain author notices/credits, replace Omni brand/logo/icons with Annie-owned assets, document source/offering requirements, and escalate incompatible licensing before making a closed-source deliverable.
3. **Do not** download an Omni APK and launch it externally, embed an entire second APK, or rewrite a lookalike GeckoView browser. Port actual source code and preserve upstream source lineage.

## Engineering scope

- Existing Annie native baseline: `annie-android/app/src/main/java/com/tomex777/annie/AnnieGeckoBrowser.kt` and GeckoView dependency; existing script WebView profiles are security-scoped and **must remain isolated** from the standalone full browser.
- Create a clean `browser/omni/` or `browser/engine/` internal package with original upstream copyright; adapt upstream ViewModel/GeckoRuntime/GeckoSession/extension management/downloads/settings/menus/services as an integrated Annie feature.
- Reconcile Gradle & Android manifest, resources, file providers, runtime singleton and application context, back/navigation handlers, permission dialogs, cookie/download/media boundaries, lifecycle, process death. Avoid two duplicate GeckoView engines or two standalone browser buttons.
- Inventory **all Omni advertised features** (tabs, bookmark/history, privacy modes, extensions, content blocking, search, downloads, page tools, media player, PDF, QR, translator, vault, sync, etc.). Port/maintain real supported ones and record justified integration blockers; do not advertise nonworking entries. Clearly gate anything requiring external auth/network/providers.
- Preserve `minSdk 26`, `arm64-v8a`, project package ID, existing history/downloads where migration is feasible. No silent data resets.

## Coordinate with Agent 2

Agent 1 owns **upstream code and engine services**; Agent 2 owns Annie-facing UI. Expose a documented stable browser state/action interface for tabs, navigation, downloads, history, permissions, fullscreen, loading, content. Agent 2 must not reimplement the upstream browser with fabricated controls. Avoid both editing `AnnieGeckoBrowser.kt` concurrently; agree file boundaries before merge.

## Pass conditions

- A commit manifest of upstream files (path, source SHA, local path, modifications, licensing).
- Build and launch actual integrated Omni-derived browser within Annie on API 26 and API 36; demonstrate page load, history, downloads, tabs, cookies/private isolation and at least one extension or documented measured incompatibility.
- A documented source-available distribution path with notices if released; safe no-assumption personal-only testing does not excuse omissions.
- PR links, logs, emulator screenshots (home + page + tabs), dependency/security report; no claims about untested features.

**Blockers to report:** upstream version/API incompatibility, package duplication, licensing or permissions. Never substitute “Omni-inspired” UI without flagging that it fails the user request.
