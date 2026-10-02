# AOD

Independent local-first Android design studio. Package `com.homira.aod`; API 26–36.

`Domain` owns portable schema v2, migration from v1, validation, schedules, transforms, history and pixel offsets. `Store` saves committed edits through AtomicFile and imports images into content-addressed private storage. `Surface` is the shared renderer for Studio, Preview, Charging and DreamService. `LiveState` subscribes to battery, alarm, calendar and active media sessions only while a surface is attached. `Notifications` handles system notification callbacks without logging content.

Ambient uses the system screensaver configuration. Charging is a user-opened bedside Activity. Lock screen is explicitly user-opened and uses show-when-locked; there are no background activity launches, accessibility hacks, global brightness writes, or wake locks. Ambient always uses black and dims image treatment. Notification previews are opt-in and only public content is eligible. Calendar, media and personal text are hidden on ambient surfaces by default.

Build/test authority: `.github/workflows/aod-android.yml`. No local Gradle acceptance is claimed. Release version is 0.1.0. A release is accepted only after the complete CI matrix passes.

Production signing consumes AOD_RELEASE_KEYSTORE_B64, AOD_RELEASE_STORE_PASSWORD, AOD_RELEASE_KEY_ALIAS and AOD_RELEASE_KEY_PASSWORD repository secrets. No private key is committed. Unsigned builds must not be represented as production installable releases. This implementation has no native libraries: the universal APK is ARM64-compatible without misleading empty ABI splits.

The permanent PKCS12 signing identity is pinned by `production-cert.sha256`. CI rejects a production APK with a different certificate. The encrypted keystore and its recovery credentials are supplied separately to the owner. Preserve them for every future release; they are not repository files. The four AOD signing secrets are configured in repository Actions secrets. For each configured production build, the acceptance matrix installs the pinned production APK on API 26 and API 36, verifies real Home/Studio/Preview rendering and saved edits, then reinstalls the same APK and repeats the acceptance flow. The production artifact is accepted only when both jobs pass. Preserve the CI reports, screenshots and certificate verification alongside each delivered release.
