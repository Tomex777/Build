# AOD

Independent local-first Android design studio. Package `com.homira.aod`; API 26–36.

`Domain` owns portable schema v2, migration from v1, validation, schedules, transforms, history and pixel offsets. `Store` saves committed edits through AtomicFile and imports images into content-addressed private storage. `Surface` is the shared renderer for Studio, Preview, Charging and DreamService. `LiveState` subscribes to battery, alarm, calendar and active media sessions only while a surface is attached. `Notifications` handles system notification callbacks without logging content.

Ambient uses the system screensaver configuration. Charging is a user-opened bedside Activity. Lock screen is explicitly user-opened and uses show-when-locked; there are no background activity launches, accessibility hacks, global brightness writes, or wake locks. Ambient always uses black and dims image treatment. Notification previews are opt-in and only public content is eligible. Calendar, media and personal text are hidden on ambient surfaces by default.

Build/test authority: `.github/workflows/aod-android.yml`. No local Gradle acceptance is claimed. Initial version remains 0.1.0 until product/release acceptance.

Production signing consumes AOD_RELEASE_KEYSTORE_B64, AOD_RELEASE_STORE_PASSWORD, AOD_RELEASE_KEY_ALIAS and AOD_RELEASE_KEY_PASSWORD repository secrets. No private key is committed. Unsigned builds must not be represented as production installable releases. This implementation has no native libraries: the universal APK is ARM64-compatible without misleading empty ABI splits.
