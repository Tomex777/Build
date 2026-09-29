# Nami Extension API v1

Nami owns its anime source contract. Compatibility with arbitrary Aniyomi/Tachiyomi APKs is
best-effort and is not the definition of the Nami platform.

## Package metadata

A Nami extension is a separately installed Android APK that opts in with:

```xml
<uses-feature
    android:name="app.nami.extension"
    android:required="false" />

<application>
    <meta-data
        android:name="app.nami.extension.api"
        android:value="1" />
    <meta-data
        android:name="app.nami.extension.provider"
        android:value=".NamiProvider" />
    <meta-data
        android:name="app.nami.extension.name"
        android:value="My Anime Sources" />
</application>
```

The provider class must have a public no-argument constructor and implement
`NamiExtensionProvider`.

The real KayoAnime source at `extensions/kayoanime` and the sample at
`test-fixtures/nami-native-extension-fixture` both use the Nami-owned API. KayoAnime exercises
real search, details, Drive episodes, structured stream headers, downloads, and VLC offline
playback. The fixture shows host-rendered configuration and contract behavior; its `example.invalid`
media URL is intentionally non-playable.

Extensions should compile against `:core:source-api` as compile-only/provided API code. Do not
bundle a private copy of Nami's API classes.

## Stable identity

- `extensionId` must stay stable across updates.
- Every `SourceMetadata.id` must stay stable across updates.
- Keep the Android `applicationId` stable for package updates. Android `versionCode` and
  `versionName` identify the installed extension build.
- Both the manifest API version and `NamiExtensionProvider.apiVersion` must match the API used to
  compile the extension. Nami rejects either mismatch as incompatible before adding its sources to
  discovery.
- Native extension sources use `SourceOrigin.NATIVE_NAMI`.
- Nami persists source/anime/episode IDs in Library, history and downloads.

Changing persisted IDs breaks resume and offline state.

## Source surface

`NamiAnimeSource` is the v1 source surface:

- search
- details
- episodes
- resolve media

Capabilities in `SourceMetadata.capabilities` declare optional behavior such as Popular, Latest,
streaming, downloads and configuration.

Sources return Nami-owned models only.

The overloads that accept `sourceState` are the process-death boundary. Treat that value as opaque
source-owned data: encode only what the source needs to reopen the anime or episode later, keep it
small, and keep it backward-compatible when possible. Do not put temporary stream URLs, cookies,
tokens, or user secrets in stable anime/episode identity.

`ResolvedMedia` carries playback/download data including URL, MIME type, quality, request
headers, subtitles, audio tracks, expiry, refresh token and optional host label.
Return quality alternatives as separate `ResolvedMedia` items and keep request headers structured;
Nami forwards them to playback and downloads.

## Playback and temporary media URLs

An `EpisodeRef` is durable identity. A `ResolvedMedia.url` is not.

Sources should expect Nami to call `resolve(...)` again after a playback failure, retry, process
restart, quality change, or download recovery. Expiring URLs should therefore be regenerated from
the stable episode identity plus its `sourceState`. Set `expiresAtEpochMillis` and
`refreshToken` when they are useful to the source, but never require the host to persist the media
URL itself as episode identity.

Keep HTTP headers in `ResolvedMedia.headers`. Do not encode required Referer, Cookie, User-Agent,
or authorization values into display labels. Source logs must not print secrets.

## Downloads

Extensions describe downloadable media; Nami owns the durable download queue, foreground service,
storage destination, partial-transfer bookkeeping, retry policy and local-file lifecycle.

If `SourceCapabilities.downloadable` is true, `resolve(...)` must return at least one media item
that the host can download. When a transfer fails and is retried, Nami may resolve the episode again,
so a source must not assume a previously returned URL is still valid. Stable source/anime/episode IDs
and source state are what allow downloads to survive process recreation and URL expiry.

Do not write directly into Nami's database or download directories from an extension.

## Browser verification

Use `NamiSourceException` with `VERIFICATION_REQUIRED` when a source cannot proceed until the
user completes a browser/cookie challenge. The exception should describe the source problem without
including cookies, tokens, page HTML or other secrets.

The host owns the verification UI and cookie lifecycle. After the user completes verification, Nami
retries the source action. Extensions should therefore make the failed operation safe to repeat and
should scope any web state to the source/origin that requires it rather than depending on unrelated
site cookies.

## Configuration

A provider receives `NamiExtensionHost`, a small host-owned preference store. A source may also
implement `NamiConfigurableSource` and publish host-rendered Toggle, Text or Choice settings.
Nami renders native-source settings in its own settings screen and persists values through the
host preference store. If `SourceCapabilities.configurable` is true, the source must implement
`NamiConfigurableSource`; the runtime rejects mismatches as a broken extension.

Extension code should not depend on Nami Compose/UI classes.
Preference keys should stay stable across updates. Choice settings should use only their declared
values, and secret setting values must not appear in logs or user-visible error messages.

## Errors

Expected failures should use `NamiSourceException` with one of the stable categories in
`NamiSourceErrorKind`: NETWORK, TIMEOUT, VERIFICATION_REQUIRED, NOT_FOUND, STREAM_UNAVAILABLE,
INCOMPATIBLE, TEMPORARY or UNKNOWN.

The host maps those categories to user-facing messages and keeps implementation detail in logs.

## Discovery and install/update

Nami discovers installed APKs that declare the Nami feature and v1 metadata. API-version
mismatches are rejected cleanly.

Installation, update and uninstall remain explicit Android package actions. Nami must never
silently install or overwrite a source package.

## Compatibility adapter

The Aniyomi compatibility adapter is isolated from this API. It can remain useful for some
external extensions, but it is optional coverage and must not block Nami product releases.

## Verify an extension

From `apps/nami`, build the sample APK with:

```sh
gradle :test-fixtures:nami-native-extension-fixture:assembleDebug
```

Install the APK before launching Nami. The API 36 instrumentation smoke verifies discovery,
metadata/version reporting, configuration, search, details, episodes, structured HLS metadata and
headers, and immediate source disable/enable behavior.
