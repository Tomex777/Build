# Operation registry: decisions recorded during M0b

## Gate order (confirmed against `OperationRegistry.invoke`)
1. Operation exists (`UNSUPPORTED` otherwise)
2. Caller is an imported package (`NOT_A_PACKAGE`)
3. Every capability declared (`NOT_DECLARED`)
4. Every permission declared (`NOT_DECLARED`, `permission` set)
5. Every permission granted (`NOT_GRANTED`, `permission` set)
6. Input is exactly one JSON object (`INVALID_ARGUMENT`)
7. Schema validation (`INVALID_ARGUMENT`)
8. Input size limit (`RESOURCE_LIMIT`)
9. Provider available (`UNAVAILABLE`)
10. Provider runs; output size limit (`RESOURCE_LIMIT`, never truncated); any provider failure, typed `AnnieError` or plain exception, must carry a declared code, otherwise it becomes `INTERNAL`

A call failing two gates reports the earliest one. `OperationConformanceTest.l2_gatesReportInOrderPackageDeclaredGranted` pins steps 2-5.

Note: steps 6-7 run before the size limit (8), so a payload that is too big *and* malformed reports `INVALID_ARGUMENT`.

## JSON Schema subset the validator supports
`type`: string | object | array | number | boolean; `required`; `minLength`; `maxLength`; `pattern` (full match);
`enum` (strings); `additionalProperties: false` at every declared object level (via `OperationProperty.nested`); type `any`. Every object property must declare `nested` or `freeForm = true` (L1 enforces this). Free-form values (e.g. download `headers`) are validated by the provider.
Not supported yet: numeric min/max, `format: http-url` (downloads.start validates the URL in the provider instead),
array item schemas. Adding any of these is a registry change plus a matching L2 generator.

## Legacy messages
`OperationProperty.invalidMessage` keeps the pre-registry English text for length/pattern/enum failures, so the
registry path matches the old bridge. Only the typed error `code` is new.

## Legacy (apiVersion 1) vs strict
The registry now reads `OperationInvocation.validationMode`, populated from the package manifest: unknown
fields are ignored in LEGACY and cause `INVALID_ARGUMENT` in STRICT, including nested objects. The registry
keeps STRICT as the default for direct/test invocations. Required-field, type and permission gates remain
enforced in both modes. Existing legacy JS wrappers may independently drop unknown options. The complete
new apiVersion >=2 JavaScript wrapper/sunset migration is still to do; current API remains version 1.

## services.call error transport (step 7)
- A provider JS error carrying an upper-case string `code` is returned as an `__annieError` marker by `__annieInvokeService`; the host re-raises it with `code`, `operation`, `retryable`, `retryAfterMs`, `permission` intact (`decodeServiceError`). Unknown codes become `INTERNAL`; message is redacted via `safeError` and capped at 500 chars; no cause/stack crosses.
- Uncoded provider failures stay `INTERNAL` with a redacted message.
- Host-side gate failures of `services.call` keep their legacy English text but now carry codes: capability/permission/dependency not declared -> `NOT_DECLARED` (+`permission`), ungranted -> `NOT_GRANTED`, provider not installed / service not declared -> `NOT_FOUND`, provider disabled / not running -> `UNAVAILABLE`, nested call / version or contract mismatch -> `UNSUPPORTED`, oversize in/out -> `RESOURCE_LIMIT`, bad JSON input -> `INVALID_ARGUMENT`.
- Decision to confirm: a provider's *own* gate errors (e.g. its NOT_GRANTED for a permission the provider lacks) are passed through unchanged per the contract, so the caller can see a `NOT_GRANTED` that is about the provider, not itself. `operation` identifies the inner op.

## Generated typings (step 6, L5)
- `OperationDefinition.js` (`JsBinding`) is the per-runtime ergonomics layer: `path` override (legacy `android.deviceInfo`), `positional`, `optionsKeys`, `spreadArg`, `returns`. L1 checks every schema property is reachable through exactly one of them and that JS paths are unique and never both a method and a namespace.
- `OperationTypings.dts()` generates `docs/generated/annie.generated.d.ts` (`interface Annie`, `AnnieError`, `AnnieErrorCode`). `OperationGeneratedArtifactsTest` fails on drift; regenerate with `ANNIE_UPDATE_GENERATED=1 ./gradlew testDebugUnitTest --tests '*OperationGeneratedArtifactsTest*'`.
- `scripts/check-annie-types.sh` compiles `docs/types-sample` (every operation, plus `@ts-expect-error` negatives) with `tsc --noEmit`.
- Not generated yet: spec text and plain-language permission bundles (need the capability/permission constants and human strings); typings for non-registry APIs (http, files, env, schedule, ...) which stay hand-written in `annie-base.d.ts` until they join the registry. Result types are only precise where observed (device.info, tts.stop, downloads.*); the rest are `Record<string, unknown>`.

## Command collisions (step 8)
Implemented in `CommandCanonicalizer` (pure, unit tested by `CommandCollisionTest`); `ScriptWorkspace` delegates to it.
- Nothing is dropped. Oldest install (ties: lowest script id) keeps `/<name>`; each newer one becomes `/<package-slug>:<name>` (`-<scriptId>` is added if two slugs still clash). Matching is case-insensitive.
- Slug: lower-case package id, runs of characters outside `[a-z0-9_-]` become `_`, trimmed of `_`/`-`, max 48 chars, falls back to the script id.
- Renamed commands lose their aliases. An alias equal to another command's final name, or already claimed by an older command, is removed from the newer one and flags it as a collision.
- `ScriptCommand.collision` is true for every involved command; `collidesWith` is the package that kept the plain name. `annie.commands.list()` now includes `collidesWith`.
- Each collision writes a WARN line to that package's log at load time. The Extensions screen warning is not wired: `ExtensionsManagerContent` was not in the supplied files. It should read `collision`/`collidesWith` from the command list.
- `registered.values.distinctBy { it.name }` inside one runtime is harmless (the map is already keyed by name; a package re-registering a name replaces its own earlier command).

## Steps 10-11 status
Wired via `scripts/repair-m0b-merge.py` on a full repository checkout (see MERGE_NOTES.md):
- `AnniePackageArchive.decodeManifest` now calls `ApiCompatibility.check(apiVersion, requires.annie, CURRENT_API_VERSION)` instead of exact equality, parses and validates `publisher` and `network.hosts`, and `encodeManifest` writes them back. `AnniePackageManifest` gained `requires`, `publisher`, `networkHosts` and `validationMode`.
- With `CURRENT_API_VERSION = "1"` nothing changes for existing packages: apiVersion 1 stays legacy-permissive, a package declaring apiVersion 2 is still rejected (with a clearer message). The registry already reads `manifest.validationMode` through `OperationInvocation`; apiVersion 2 remains unsupported until the host API level is raised and JS-wrapper migration is completed.
- `network.hosts` is a declaration only. `http.request` and downloads do not consult `ManifestIdentity.hostAllowed` yet; the Extensions card shows them as "Reach: ...".
- Tests: `AnniePackageManifestTest` (JVM), `ApiCompatibilityTest`.

## M0.5: message handles, live updates, completion (done)
- `messages.send(message) -> { id, packageId, conversationId, createdAt }` and `messages.update(id, message)` are registry operations (capability `messages`, permission `messages.post`, `ScriptMessages.kt`). The handle id is a host-minted random token stored on the chat entry (`scriptHandleId`); a package can only update messages it created, and gets `NOT_FOUND` for anything else. Nothing is held in QuickJS. Rules: type 1-32 chars and a known native type (`error` is reserved), 48 KiB cap, 20 sends per minute per package (`RATE_LIMITED` with `retryAfterMs`), `send` needs a chat (from a command or action).
- A progress message updates in place: `const h = await annie.messages.send({type:"progress",...}); await annie.messages.update(h.id, {type:"progress",...})`. `update` also accepts the handle object.
- Live UI: `ChatHistoryStore.changes` announces every append/update that does not come from the UI. `AnnieChat` merges them into the in-memory lists (and does the same before each `persistHistory`, which previously could overwrite messages that a background worker had appended).
- Download completion: `appendScriptResult(..., idempotent = true)` makes the completion append at-most-once per `download:<id>`; an action that cannot run (package disabled, not registered, returned nothing) or fails after its retries is logged and cleared instead of staying pending forever.
- Not done: SSE/chunked/WebSocket event-stream messages (`stream`), and cancelling a queued completion job when a package is disabled (harmless now: the worker drops it).
