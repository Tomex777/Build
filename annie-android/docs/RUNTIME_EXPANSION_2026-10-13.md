# Annie runtime expansion — local implementation awaiting Android verification

Branch: `annie-typescript-runtime-20261013`, based on `annie-roadmap-audit-20261012`.
The user's “ts” meant “this shit”; scope was corrected to all seven remaining areas.
Nothing in this branch has been merged or released. Publication was approved and the work
is in draft PR #181. Android acceptance and full regression/release workflows are running;
the current commit must pass before any completion or release claim.

## Implemented in source

- Offline TypeScript 5.9.3 transpilation in a separate QuickJS compiler runtime, syntax
  diagnostics, typed entry points and module loading. No semantic type checking.
- CPython 3.10 via Chaquopy 17.1.0 in a non-exported Android worker process. JSON-only
  inputs/results, restricted builtins/attributes, math imports, instruction/deadline
  checks and a parent-enforced worker timeout. This is a restricted computation
  environment, not unrestricted Python: filesystem, networking, Java, classes, arbitrary
  imports and third-party pip packages are unavailable. The worker shares Annie's UID;
  process separation alone is not an OS security sandbox. AST restrictions are essential.
- LuaJ 3.0.1 (Lua 5.2) in the same worker design. No io/os/luajava/package/debug/load access;
  bounded instructions and JSON conversion with cycle/depth checks. Fresh globals per call.
- JavaScript/TypeScript can orchestrate package-local Python/Lua files through
  `await annie.runtime.call(path, input, functionName?)`. Guests define `main(ctx)`;
  standalone guest entry points register a command automatically. No guest-to-host objects.
- Native `annie.ai.generate(prompt)` and `annie.ai.stream(prompt)` via the existing operation
  registry. Fixed Anthropic HTTPS endpoint, no redirects, host-only encrypted credentials
  in a separate preferences namespace, explicit `ai` capability and `ai.generate` grant.
  Stream additionally needs `messages` and `messages.post`; grants are rechecked during
  response processing. Five requests per package per minute and bounded input/output.
  Script Studio's Host AI dialog lets the user configure/remove the key and model.
- `annie.messages.stream()` appends, finishes or cancels one persistent mutable message.
  AI streaming consumes text deltas from SSE and persists incremental updates to one handle.
- API 2 current version, strict operation schemas and operation `since` enforcement; legacy
  scripts/manifests without an explicit version retain API 1. Permission reconciliation
  keeps only grants still declared; new grants are never implied by a version upgrade.
- WebGL acceptance test compiles/link shaders, draws a triangle, reads exact pixels,
  resizes, expands fullscreen and checks the absence of the Annie bridge.
- Normalized the repository's pre-existing required repair transforms into the source;
  running the repair script again makes no changes.

## Local checks completed

- Four TypeScript compiler tests, two message-stream tests and three API 2 wrapper tests pass in Node.
- Five Python computation/restriction tests pass in CPython.
- Lua Kotlin source compiled with LuaJ; functions/loops/JSON, absence of host libraries,
  and infinite-loop termination pass in the JVM proof.
- Native AI provider, registry, compatibility and typings Kotlin sources compile against
  lightweight Android storage stubs; mock-provider checks pass for incremental updates,
  missing/revoked grants, API 1 rejection of API 2 operations and unknown fields.
  These checks are not Android compilation or device verification.
- TypeScript declaration samples and seven repair-contract fixtures pass.

## Pending validation — completion gates

1. Draft PR #181 targets the audit branch. Do not merge until the checks below pass.
2. Run the prepared `annie-file-types.yml` workflow on Android API 26 and 36. It now installs
   matching build Python and runs TypeScript/Python tests, Kotlin/JVM tests, APK compilation,
   GuestRuntimeTest, HostAiRuntimeTest, TypeScriptRuntimeTest, AnnieCanvasMessageTest and
   existing Studio/image acceptance classes. Inspect failures and repair them.
3. Verify Chaquopy packaging/startup in the worker on both ABIs and lifecycle behavior after
   timeout, cancellation, package disable and repeated cross-language calls. Exercise hostile
   computation inputs and review the guest restrictions before release.
4. Verify host credential storage on Android, live provider SSE behavior, foreground UI
   incremental rendering, permission revocation during a request, network loss and teardown.
   Local AI tests use mocks; no paid provider requests have been made.
5. Run the complete Annie regression suite and release/install/reinstall checks. No new APK
   has been produced or validated in this session. WebGL has a test, but no passing device
   evidence yet. Do not mark the seven areas fully complete based only on local checks.
