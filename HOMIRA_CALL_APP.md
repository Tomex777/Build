# Homira — call-first native app

Homira is currently being built as a private, call-first native app. Chat remains intentionally parked while calls, contacts, voicemail, profile, and call reliability are completed.

## Working branch

- Repository: `Tomex777/Build`
- Branch: `homira-call-ci`
- Do not work directly on `main`.

## Platforms

- Android: Kotlin + Jetpack Compose (`homira-android/`)
- iPhone: Swift + SwiftUI (`homira-ios/`)

Android is currently ahead of iOS and is the active live-data implementation.

## Android — implemented now

The Android client is no longer only a UI shell.

- Supabase phone authentication gate
- Live profiles
- Live Homira contacts
- Add contact by exact username or phone number
- Private profile photo + call-card storage
- Keypad / Recents / Contacts / Me navigation
- Swipe right for voice call / left for video call
- Local-only persistent call history
- Missed / declined / cancelled / failed / answered outcomes
- WebRTC voice calls
- WebRTC video calls
- Front/rear camera switching
- Camera stops when video is disabled
- Remote and local WebRTC renderers
- Screen sharing through Android MediaProjection
- Mute + remote-muted state
- Speaker routing
- Low-data WebRTC profile
- Supabase Realtime signaling
- Temporary server call sessions with short ringing expiry
- Native Android incoming-call CallStyle notifications while the app process can receive Realtime
- Full-screen incoming-call intent when Android allows it
- Notification Decline action updates the live Supabase call session
- Private voicemail greeting recording and playback
- No-answer voicemail recording, review, upload, and delivery
- Received voicemail playback in Recents
- Realtime voicemail updates
- Voicemail on/off saved to the live profile
- Private voicemail Storage policies
- Block / unblock contacts
- Blocking is enforced by call-session RLS, not only by UI
- Persistent call-notification and low-data settings

## Privacy model

- Voice, camera video, and screen media use WebRTC media paths.
- Supabase is used for authentication, contact/profile state, temporary call setup/signaling, push-token registry, and voicemail metadata/private voicemail files.
- Supabase does **not** store live voice/video/screen-call media.
- Detailed call history is kept locally on the Android device.
- Profile media and voicemail buckets are private and protected by RLS/Storage policies.
- A blocked account cannot insert a new call session to the blocker.

## Networking

Current Android calls use WebRTC with STUN/direct connectivity.

TURN relay is intentionally **not** advertised as working yet because Homira does not have a deployed TURN endpoint/credential service in this branch. The “Protect IP in calls” Settings item reports that clearly rather than enabling a broken relay-only mode.

Once TURN is available, the intended path remains:

```
direct WebRTC when possible
        ↓ fallback
authenticated TURN relay
```

## Incoming calls — current limitation

Homira now has Android CallStyle/full-screen incoming-call notification plumbing, but true **killed-app wake-up** still needs FCM.

The repository currently contains no Firebase Android app configuration (`google-services.json` / Firebase project values), so Firebase credentials/config have deliberately not been invented or committed.

Already prepared for that next step:

- private `device_push_tokens` table
- per-user RLS
- Android/iOS platform field
- secure token register/remove repository methods

Once real Firebase app configuration is supplied, FCM becomes the wake-up transport; the native incoming-call UI/notification layer is already present.

## Voicemail

- User can enable/disable voicemail.
- Default or custom recorded greeting.
- Custom greeting stored privately.
- Callers can hear the custom greeting after an unanswered call.
- Message recording limit: 2 minutes.
- Caller can review/re-record before sending.
- Recipient sees voicemail in Recents and can play it.
- Listen state updates live.
- Voicemail file access is limited to sender/recipient as appropriate.

## MiMi and Hex artwork

Do not invent MiMi or Hex.

The UI must keep neutral placeholders until the real established assets are supplied. Existing placeholder markers are only temporary and are not character redesigns.

## CI

### Android

`.github/workflows/homira-android.yml` builds a debug APK from `homira-android/` on `homira-call-ci`.

### iOS

`homira-ios/project.yml` is an XcodeGen project. CI generates the Xcode project, builds the simulator app, launches it, and captures a simulator screenshot.

## Major remaining call work

- Add real Firebase Android app config and FCM wake-up delivery.
- Add authenticated TURN configuration/ephemeral credentials.
- Decide whether to integrate deeper Android Telecom self-managed calling after native notification flow is proven.
- Bring iOS live-data/WebRTC/PushKit/CallKit implementation up to Android parity.
- Replace MiMi/Hex placeholders only when the actual character assets are provided.
