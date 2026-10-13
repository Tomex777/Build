# Annie runtime expansion — implemented and emulator verified

Branch: `annie-typescript-runtime-20261013`, based on `annie-roadmap-audit-20261012`.
All seven runtime areas are implemented in PR #181. Code commit
`770983847b39d26e0eb0affd3f434363c03788a9` passed Node regression,
runtime/Studio acceptance on API 26 and 36, full Android regression, process-death
restoration, release smoke tests and owner-signed production install checks.
The validated production release is available from
https://github.com/Tomex777/Build/actions/runs/38381728899.
The API 26 full suite passed on retry after an initial Compose startup failure;
no assertions were changed for that retry. This document update changes no executable code.

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

## Android verification and remaining limits

- Real Python/Lua and TypeScript execution, mixed-language calls, worker recovery,
  namespace collisions and entry point protections pass on Android API 26 and 36.
- Host credential separation/encryption, incremental messages, SSE parsing, grant
  revocation and shared per-package request limits pass with mocked provider responses.
- WebGL shader compilation/linking, drawing, exact pixel readback, resizing and
  fullscreen pass on both Android versions.
- Debug/release APK and AAB builds, complete regression suites, process-death restore,
  release launch/render and permanently signed production install checks pass.
- Physical-phone tests and live Anthropic requests with a user-provided key remain
  unperformed. No paid provider requests were made. Network/provider behavior needs
  live validation; CI exercises controlled streams and errors.
- Python/Lua remain restricted computation environments, and TypeScript performs
  transpilation without semantic type checking. These are deliberate implementation limits.
