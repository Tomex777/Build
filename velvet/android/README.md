# Velvet — Native Android foundation

A **native Kotlin / Jetpack Compose** application, **not a WebView**. Open the `velvet/android` directory in Android Studio; or use the GitHub Actions `Velvet · Android Debug APK` workflow and download `velvet-native-debug` from its artifacts.

## What works in this first build
- Five main destinations: Home, Chat, Games, Our Story, Us.
- Fully immersive Chat with no bottom navigation; partner's avatar/name/status, a custom-drawn flame streak badge (0 before backend connection), a full-width message-row swipe gesture to quote/reply, long-press actions (copy/edit/delete/pin), local text composer.
- Heartbeat interaction on Home (local visual pulse + haptic feedback; **no chat message**).
- Games hub, playable **local** Tic-Tac-Toe, layered fully rendered question deck with interactive swipes and individual pastel colors.
- Us with own-profile editing and optional location-sharing concept (off by default; does not request or transmit location yet).
- Our Story is a **placeholder by design** while the owner chooses one of three layouts from `velvet/design/our-story-layouts.html`.

## Still to implement
- Account pairing, Supabase authentication/storage/realtime, two-device messages and heartbeats, real streak calculation, notification delivery, actual shared albums and media, Ludo and Chess engines, multiplayer games, remote online presence, location permission and opt-in sharing.
- Current messages and names are mock data held in memory only. Sending does **not** contact the other phone. An APK build does not mean synchronization is complete.

This branch stays isolated from other projects in the shared Build repository.
