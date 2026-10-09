# Mihon v0.19.9 reader upstream

Source: https://github.com/mihonapp/mihon/tree/7a917968e3bf71c4a665e6655a550877d81ead1d
Pinned commit: `7a917968e3bf71c4a665e6655a550877d81ead1d`

Yomi's embedded reader must use Mihon's original reader source, not a custom visual imitation. Source snapshots in `mihon-upstream/app/src/main/java/` are reproduced verbatim so diffs can be reviewed. Mihon is licensed Apache-2.0; retain the existing licenses and attribution.

## Adaptations in the Android app

- `eu.kanade.presentation.reader.appbars.ReaderAppBars`, `ReaderTopBar` and `ReaderBottomBar` compile in the standalone app; upstream-only dependencies are supplied through Yomi local adapters.
- `eu.kanade.presentation.reader.components.ChapterNavigator` is ported from upstream with the original slider implementation `tachiyomi.presentation.core.components.material.Slider`.
- Eight Mihon original icon drawable XML resources are included unchanged.
- `app.yomi.reader.ReaderActivity` attaches the upstream reader app bars to Yomi's local ZIP / image-folder page reader.

## Incomplete: never claim full parity from this work alone

- The bundled Mihon `ReaderSettingsDialog` is a verbatim reference snapshot **not yet integrated**; Yomi still shows its custom reader settings.
- Yomi's pager/webtoon viewers are *adapted* Mihon viewer technology, not complete unchanged upstream implementations. Additional work is required to port upstream page loading, settings, image decoding, gestures, and error presentation.
- Real device testing of missing covers, blank pages, Android picker import, and file associations remains necessary.
- CI synthetic fixtures must not substitute for real CBZ archives and UI parity comparison.

Track full parity: https://github.com/Tomex777/Build/issues/76
