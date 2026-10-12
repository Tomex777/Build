# Annie native Android roadmap audit — 12 October 2026

This audit covers the recorded October native Kotlin/QuickJS Annie requirements, Phase A→B→C→D, and the browser/voice/Canvas requests. Homira, Velvet, MSCC, and the older web prototype are separate scopes. Evidence is implementation plus named tests, not a checklist inferred from a green build.

Baseline: release-candidate commit `7f7d1cb890d7eae5bc8b8e0d0cbbb5c449ce4590`; full workflow [38251682344](https://github.com/Tomex777/Build/actions/runs/38251682344) passed both APIs and permanent-key install/reinstall. API36 contains 344 unique JVM/device cases with no failures/errors/skips. API26 has an expected MULTI_PROFILE capability assumption: unsupported scoped WebViews fail closed. Physical Samsung and long-session acceptance remain separate.

Read the effective CI source: `scripts/repair-m0b-merge.py` integrates six generated source repairs before compilation. In particular, extension command warnings, manifest identity/compatibility and browser scroll overloads must be reviewed after that step. Historical overlay notes are not current feature-status reports.

## Feature-by-feature findings

| Agreed feature | Implementation and evidence | Audit outcome |
| --- | --- | --- |
| Chat-first slash commands; no AI requirement for commands | MainActivity, ScriptWorkspace; ReportedFlowsTest, ScriptChatFlowTest, CommandSuggestionEngineTest | Implemented and baseline tested |
| Multiple chats; rename/delete; process-death persistence | ChatHistoryStore and MainActivity; ChatHistoryTest and separate process-death acceptance | Implemented and baseline tested |
| Consolidated drawer/tools; retained header Studio route | MainActivity; ChatHistoryTest and ReportedFlowsTest | Implemented and baseline tested |
| QuickJS commands, actions, sessions and project isolation | ScriptWorkspace; ScriptRuntimeTest, ScriptChatFlowTest, ScriptFilesListTest | Implemented and baseline tested |
| Package manifests, ZIP install, review-before-enable, uninstall | AnniePackageArchive, InstalledPackageRegistry, ExtensionsManager; package/manifest/import tests | Implemented and baseline tested |
| API compatibility and publisher identity | ApiCompatibility plus generated merge repair; ApiCompatibilityTest, AnniePackageManifestTest | API1 supported; API2 migration deliberately deferred |
| Registry validation, typed errors, permission gate order | OperationRegistry and AndroidOperationProvider; conformance, generated cases, service-error tests | Implemented for the registered surface |
| Generated TypeScript typings/drift checks | OperationTypings and generated declarations; OperationGeneratedArtifactsTest, operation golden tests, CI tsc check | Implemented; typings for non-registry APIs remain hand-maintained |
| Command collisions without dropping commands; visible warning | CommandCanonicalizer, runtime logs, MainActivity commandWarnings, generated ExtensionsManager repair; CommandCollisionTest and extension tests | Implemented; old documentation's “not wired” claim is stale |
| Per-package permissions; access off by default; foreground gate | AndroidOperationProvider, AndroidCapabilities, ExtensionsManager; registry/provider/extension tests | Implemented for current allowlisted operations; not arbitrary Android access |
| Device info, TTS, OCR, document picker, media metadata, notifications | AndroidCapabilities and AndroidOperationProvider; conformance/fake-backend and Android bridge tests | Implemented bounded bridge; some actual platform engines/permissions still require device/provider-specific validation |
| Package services, dependency/version checks, error redaction | ScriptWorkspace and OperationRegistry; ServiceErrorTransportTest and instrumented variant | Implemented and baseline tested |
| Package storage, files and assets | ScriptWorkspace and package asset resolver; runtime, file-list and archive tests | Implemented and baseline tested |
| Scheduling/background tasks | ScriptSchedule and ScriptTasks; ScriptScheduleTest and ScriptTaskStoreTest | Implemented and baseline tested; no promise of unbounded background execution |
| Downloads with progress, pause/resume/cancel, retry, offline reopening | DownloadManager, transfer service/downloader and Gecko download manager; media/generic/download/history tests | Implemented and baseline tested |
| Download completion action; no action on failure/cancel; idempotent delivery | ScriptDownloads, downloader completion dispatch, ChatHistoryStore | Implemented; full real process-kill-during-transfer completion scenario has less direct coverage than ordinary downloads and chat process-death recovery |
| Mutable native messages: send/update handles; ownership/rate limits | ScriptMessages and ChatHistoryStore; ChatMessageHandleTest, registry tests | Implemented and baseline tested |
| Native text/code/copy/image/music/video/file/options/form/progress/catalog messages | MessageTypeRegistry and MainActivity renderers; code/message/chat/metadata/player tests | Implemented and baseline tested |
| Canvas as an interactive chat message, expandable fullscreen, same state | AnnieCanvasMessage and StarterScripts; three native Canvas acceptance methods with touch and pixel assertions | Implemented and baseline tested |
| Offline HTML/CSS/JS mini-apps in folders | ScriptStudio stores files, but Preview previously ignored local linked CSS/JS | Confirmed functional gap repaired in this audit; new validation required |
| Local linked resources and deferred classic JS in preview | ScriptWebPreview bundles relative CSS and classic JS; path/missing/remote/module/unit tests, native interactive Canvas test | New implementation; inherits offline Canvas isolation and 48 KiB boundary |
| Autosave and failed-save draft retention | ScriptStudio; disk-write failure/recovery/Back test plus editor tests | Implemented and baseline tested; latest typed text is supplied to project preview |
| Create/edit .js/.ts/.py/.lua/.html/.css and text files | ScriptStudioFileTypes, ScriptFiles; file-type/location/native creation tests | File editing implemented; direct TS/Python/Lua execution is not implemented |
| Live command/document preview; fullscreen Canvas | ScriptStudio preview and native renderers; script/chat/Canvas tests | JS command and document previews implemented; multi-file project gap repaired here |
| Script deletion, folder/file selection, sharing, clipboard, find/replace/undo | ScriptStudio and repaired Sora editor; ScriptChatFlowTest, ScriptEditorReplaceTest; new folder/share regressions | Baseline editing tested; folder/share fixes require new validation |
| Image fullscreen viewer and editor | Viewer activity, image bounds and editor; image decode/transform/viewer/pixel tests | Fit/zoom/pan, rotate/flip/center-square/reset/save-copy implemented; freely positioned crop and image sharing are not implemented or claimed as agreed baseline requirements |
| Image saving tied to the current render | ImageEditorActivity captures the ready bitmap and blocks edits during picker/save | Implemented; transformation tests pass, but automated document-picker save-to-destination coverage remains a validation gap |
| Full browser: tabs, normal/private separation, desktop mode, navigation, uploads/downloads/history | Gecko browser; restore/UI/file-picker/end-to-end/history tests | Implemented and baseline tested; “full-fledged” is bounded by these concrete features, not feature parity with every browser |
| Browser bars and IME; native gestures; authenticated source isolation | Gecko/browser/player isolation code; UI and cookie-source tests | Implemented and baseline tested; unsupported scoped WebViews fail closed |
| WhatsApp-style voice recording, waveform, cancel, lock, pause/resume, AAC | Voice recorder and message UI; native gesture, recorder and playback tests | Implemented and tested on emulators; subjective audio quality remains device-dependent |
| Video source switching, controls, resume and local playback | MediaPlayerScreen keyed by active source, WatchHistoryStore; player/source/local-video tests | Implemented and baseline tested; actual external YouTube stale-result behavior is not certified by local-source tests |
| Manga CBZ reader and saved progress | AnnieMangaReader and imported archive library; ReportedFlowsTest | Local archive reading implemented/tested; online content requires a compatible authorized provider |
| Catalogs, anime/movies/music/source selection | MediaCatalog and package source declarations; catalog/reported-flow/source tests | Metadata/local/provider-driven flows implemented; content rights, provider availability, keys and live endpoints are external requirements |
| Optional AI authoring, proposal preview/diff/apply | ScriptAssistant and ScriptAssistUi; ScriptAssistantTest | Authoring provider boundary implemented; incorrect JS-only instructions for HTML/CSS repaired here; authenticated provider/model availability not tested without user credentials |
| Host `annie.ai.*`, AI-specific capability/message grants | No registered host AI runtime API | Explicitly deferred Phase D; Script Assist does not complete this requirement |
| Python/Lua bridge and combined runtimes; direct TypeScript execution | No Python/Lua interpreter or TS transpiler in app | Explicitly deferred architecture work; supported file extensions do not imply executable runtimes |
| WebGL and larger full-app runtime parity | Canvas uses Android WebView; tested games are 2D | WebGL-specific acceptance and broader app runtime remain unverified/deferred; do not claim complete support |
| Event-stream messages (SSE/chunks/WebSocket) | No registered `stream` message implementation | Deferred beyond existing mutable progress messages |
| Per-host network allowlist and API2 strict wrappers | Manifest declares network.hosts; API1 network permission gates apply | Host list is descriptive, not an enforced per-host firewall; API2 migration remains deferred, as recorded in original registry decisions |
| Permanent signing and update-compatible identity | Pinned certificate, signed ARM64/universal APKs/AAB and install/reinstall workflow | Implemented and baseline verified |

## Confirmed repairs in this audit

1. Project preview now resolves relative local stylesheet/classic-JS dependencies from the project snapshot, includes unsaved selected-file edits, preserves classic script order and runs deferred scripts after the document content. Missing dependencies, project escapes, remote URLs and module imports produce visible errors rather than silently broken previews. Network/device bridges remain disabled.
2. File selection now reads the latest saved source from disk rather than the stale project snapshot. The native failure/recovery test switches back and asserts that the Sora editor contains the retained edit. Preview also refreshes saved project dependencies before bundling.
3. Script Assist system/user instructions now respect the selected file's language. HTML/CSS files are no longer requested as QuickJS JavaScript commands, and JavaScript belonging to an HTML project receives browser/DOM instructions rather than QuickJS command instructions; deferred runtime types are identified honestly.

4. Canvas and bundled-resource closing-tag escaping now uses literal replacement callbacks. Regex replacement strings had consumed the intended backslash, so snippets containing `</script>` or `</style>` could prematurely end their inline tag. New tests require exactly one real closing tag for each wrapper.

5. Folder selection now loads that project's entry file instead of changing selection IDs while keeping another document's editor contents. A native regression asserts the displayed entry and both projects' persisted contents.
6. Script sharing uses the current selected draft or fresh disk bytes, the correct HTML/CSS/JS/JSON MIME type, and an independent file snapshot per share. A native FileProvider test verifies readable content, read grants, MIME type and that later shares cannot overwrite an earlier snapshot.

The first full audit run passed the new native editor/Canvas checks on API26 but exposed an intermittent Gecko history test failure. Its first-page native tap had no observable acknowledgement. The fixture now changes its title only when a real native tap reaches the page, and navigation waits for that acknowledgement before retaining the original Back/Forward and exact-download-byte assertions. No browser history protections or assertions are removed.

The new code must pass its own Android/CI run before it replaces the delivered baseline APK. New tests are not retroactively counted as baseline coverage.

## Work not silently classified as complete

Direct Python/Lua/TS execution, mixed runtime bridge, host AI API/grants, stream messages, API2 wrapper migration and WebGL-specific acceptance remain explicit roadmap items. Device/manual UX, real external provider behavior, save-picker destination assertions and process-kill-during-download-completion coverage are validation work. A green baseline APK alone does not close them.
