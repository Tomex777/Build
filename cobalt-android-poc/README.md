# Cobalt Android POC

Goal: run Cobalt's linked WhatsApp client inside a normal Android APK with minSdk 26, without Termux, Node.js, a VPS, or rewriting WhatsApp's protocol.

## Phase 0: untouched Cobalt package test

The app deliberately depends on `com.github.auties00:cobalt-lib:0.1.0` while avoiding compile-time references to its Java-25 classes. CI lets D8 inspect the real published artifact first.

If that packages, the fork can be tiny. If it does not, the build log identifies the first concrete incompatibility to patch.

## Android fork strategy

Keep the linked/Web transport, pairing, Signal/session keys, stanza/message codecs, chat/message/contact sync, send/receive, receipts, typing, reactions, and groups.

Replace or exclude:
- `Thread.ofVirtual()` with Android executors/coroutines.
- `java.lang.foreign` FFmpeg/call code from the Android core.
- `jdk.incubator.vector` with Cobalt's existing scalar fallbacks.
- Java 21+/25 collection conveniences with Java-17-compatible equivalents.
- Desktop persistence/native-loader assumptions with Android storage/Room where needed.

## Milestones

1. D8/package test.
2. Load linked-client classes.
3. Pairing-code flow.
4. Persist linked credentials.
5. Reconnect.
6. Receive one text message.
7. Send one text message.
8. Build the proper Kotlin/Compose client UI.

Cobalt is MIT licensed; any source we carry forward must retain its license and attribution.
