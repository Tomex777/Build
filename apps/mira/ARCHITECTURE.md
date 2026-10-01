# Mira architecture

Mira is independent from Nami at the application boundary.

Dependency direction:

app
  -> core:domain
  -> core:source-api
  -> core:source-runtime
  -> extensions:internetarchive
  -> extensions:tvmaze

The domain models Movies and TV Series explicitly. Series expose seasons and episodes; movies do not manufacture fake seasons or episodes.

Sources own catalog/search/detail/stream resolution. The host app owns UI, navigation, VLC playback, downloads, persistence and source management.

The initial source proof intentionally combines two real providers with different capabilities:
- Internet Archive proves movie catalog, details, playable media and downloadable files.
- TVMaze proves TV catalog, seasons and episode metadata.

A source advertises capabilities so the UI never shows a Play or Download action that cannot actually work.

## Extension isolation

The host ABI uses app.mira.source and app.mira.domain. APK discovery requires
the app.mira.extension feature and app.mira.extension.* package names. Providers
must identify themselves by their APK package name; source IDs must start with
that identity followed by a colon. No Nami/third-party catalog is configured.
Preferences and enablement are stored in Mira's private application sandbox.
The sample APK compiles against the host contracts as compileOnly dependencies.

## Release status

Production signing requires the four MIRA_RELEASE_* repository secrets.
Acceptance candidates are explicitly unsigned; production release runs only
after successful Mira Android acceptance for the identical commit. Permanent
key existence, certificate consistency, final production runtime, complete
feature parity, and visual QA remain required before declaring Mira finished.
