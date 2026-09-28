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

Extensions should compile against `:core:source-api` as compile-only/provided API code. Do not
bundle a private copy of Nami's API classes.

## Stable identity

- `extensionId` must stay stable across updates.
- Every `SourceMetadata.id` must stay stable across updates.
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

`ResolvedMedia` carries playback/download data including URL, MIME type, quality, request
headers, subtitles, audio tracks, expiry, refresh token and optional host label.

## Configuration

A provider receives `NamiExtensionHost`, a small host-owned preference store. A source may also
implement `NamiConfigurableSource` and publish host-rendered Toggle, Text or Choice settings.

Extension code should not depend on Nami Compose/UI classes.

## Errors

Expected failures should use `NamiSourceException` with one of the stable categories in
`NamiSourceErrorKind`: NETWORK, TIMEOUT, VERIFICATION_REQUIRED, NOT_FOUND, INCOMPATIBLE,
TEMPORARY or UNKNOWN.

The host maps those categories to user-facing messages and keeps implementation detail in logs.

## Discovery and install/update

Nami discovers installed APKs that declare the Nami feature and v1 metadata. API-version
mismatches are rejected cleanly.

Installation, update and uninstall remain explicit Android package actions. Nami must never
silently install or overwrite a source package.

## Compatibility adapter

The Aniyomi compatibility adapter is isolated from this API. It can remain useful for some
external extensions, but it is optional coverage and must not block Nami product releases.
