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
