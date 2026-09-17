# Homira — call-first native app

This branch is the first native call-first Homira build.

## Current scope

- Android: Kotlin + Jetpack Compose (`homira-android/`)
- iPhone: Swift + SwiftUI (`homira-ios/`)
- Matching Calls / People / You navigation on both platforms
- Quick voice/video call entry points for MiMi and Hex
- Animated active-call shell with mute, speaker, video, screen-share preview, timer and end-call controls
- Privacy-first copy and architecture placeholders
- Chat is intentionally out of scope for this pass

## MiMi and Hex artwork

The UI intentionally uses neutral marker slots (`✿` for MiMi and `⚡` for Hex) until the real established character assets are supplied. Do not replace them with newly invented mascot designs.

## Planned call architecture

The native UI is deliberately separated from transport so it can later be wired to:

- WebRTC for voice/video/screen sharing
- Supabase Auth + private Realtime channels for signaling/presence
- FCM on Android and APNs/PushKit + CallKit on iOS for incoming calls
- STUN for direct-path discovery
- coturn on the small Azure VM as TURN fallback only when direct P2P cannot connect

The intended privacy model is minimal server metadata, no Supabase storage of voice/video/screen content, and detailed call history kept locally where practical.

## Android build

The CI workflow builds a debug APK from `homira-android/` on branch `homira-call-ci`.

## iOS build

`homira-ios/project.yml` is an XcodeGen project definition. CI generates the Xcode project and performs an unsigned simulator build so the SwiftUI client stays build-verified without needing signing credentials.
