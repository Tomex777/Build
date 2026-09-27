# Torri reader module boundary

Torri keeps Mihon v0.19.9's reader implementation intact. This note defines the extraction seam for a future standalone reader without creating that app or forking the reader today.

## Preserve as reader core

The low-level reader/viewer stack is the strongest reusable boundary:

- `ui/reader/model/ReaderPage`
- `ui/reader/model/ReaderChapter`
- `ui/reader/model/ViewerChapters`
- `ui/reader/viewer/Viewer`
- pager and webtoon viewer implementations
- reading-mode/orientation models and reader-only preferences
- page loading primitives that can be supplied by a host

Do not add Torri library, browse, extension-manager, or navigation dependencies below this boundary.

## Current host coupling

`ReaderViewModel` still coordinates app-level concerns in addition to the reading session:

- manga/chapter database lookup
- source resolution
- download lookup
- history/progress persistence
- tracking updates
- manga viewer flags
- cover editing and image saving

`ReaderActivity` also owns Android/Torri host behavior such as app navigation, notifications, web-view launching, sharing, and lifecycle/window integration.

Those responsibilities should remain working in Torri for now. They are the adapter layer to peel away later, not code to move into viewer implementations.

## Future session contract

A later extraction should make the reader consume a small host-facing session contract conceptually shaped like:

```text
ReaderBook
  id
  title
  initial chapter
  reading settings

ReaderChapterSource
  chapter metadata
  page list / page streams
  previous + next chapter

ReaderProgressSink
  on page selected
  on chapter completed
  on session closed

ReaderHostActions
  share/save page
  open external source (optional)
  set cover (Torri adapter only)
```

Torri can then adapt its existing database/source/download/history/tracking services into that contract. A future standalone reader can provide file/archive/document adapters without importing Torri's library or extension UI.

## Rules for new Torri reader work

1. Keep pager/webtoon/viewer code independent of Torri branding and library screens.
2. Pass page/chapter/session data downward rather than giving viewers repositories or source managers.
3. Keep history, tracking, downloads, source lookup, and library mutations in the Torri host adapter.
4. Prefer callbacks/events from reader core to host services.
5. Do not change serialized manga/chapter data merely to prepare extraction.
6. Preserve current paged, LTR, RTL, vertical, webtoon/long-strip behavior during any boundary refactor.
7. Extraction happens only when Torri reader behavior is covered by runtime smoke tests.

This is intentionally an incremental boundary definition, not a second reader implementation.
