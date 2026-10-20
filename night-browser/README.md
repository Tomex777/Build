# NIGHT Browser — Android prototype 0.1

Native Android browser experiment built on GeckoView. Package: `com.nice.browser`. Minimum: Android 8 (API 26).

## Functioning scope

- Real GeckoView web navigation, search/address input, back/forward/reload, multi-tab switching and site titles.
- Journeys automatically record local navigation events as parent-linked trees without AI. Create new Journeys and revisit nodes.
- Long-press a link to Peek at it in a disposable browsing session, open it in a new tab, or copy the URL.
- Local bookmarks and previously recorded Journey entries.
- Time Machine alpha: explicitly save a **PDF copy** of the current page, then open the copy in an installed PDF viewer. This is *not* a restorable interactive webpage.
- Inactive Gecko sessions receive `setActive(false)` to reduce memory usage.
- All locally stored history is kept in private app storage. No AI browsing-data uploads.

## Intentionally NOT implemented in this first prototype

AI Universal Search (needs explicit opt-in model/backend and clear privacy policy); Site Studio (requires safe per-domain modifications); a full resumable download manager; sync; sophisticated permission/file-upload dialogs; offline complete-site archives; anti-phishing lists; extension support; private profiles. Do not describe these as shipped.

## Build

`gradle -p night-browser :app:testDebugUnitTest :app:assembleDebug` using Java 17, Android SDK 37.1, and Gradle 9.7.1.

GitHub Actions uploads the **debug APK** as an artifact for this development branch. Debug APK is not a production-signed release. Test on a real Android phone before making production claims.

## Test targets

Normal page loading, redirects, login pages, tab switching, app resume, Journey branching, Peek dismissal, link popups, PDF saving/opening, rotated layout, slow network and low-memory behaviour. Record actual results and address failures in subsequent iterations.
