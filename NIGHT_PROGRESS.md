# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Last fully verified app commit: `6a164615f9cf8e762f2e1ddf2a35a6469ff131e3`
- Verified CI:
  - Night Integrated Regression `35622088876`: PASS
  - Android 16 build: PASS
  - Android 16 Live Voice UI: PASS
  - Android 16 provider HTTP failover: PASS
  - Android 16 video: PASS
  - Android 16 message blocks: PASS
  - Android 16 image editor: PASS
  - Android 16 PDF editor: PASS
  - Android 16 Mihon reader: PASS
- Completed major systems: GROQ encrypted multi-key pool + rotation/cooldown/failover; real provider HTTP instrumentation; full-screen PDF viewer/editor derivative-copy flow; image/video regression coverage; Mihon reader regression; rich message regressions.
- Night calling rule: Night is user ↔ AI only. No Homira/person-to-person/WebRTC/FCM calling. No Calls bottom-navigation tab. The phone icon inside an AI chat starts the voice-only Live Voice conversation directly; no video-call option or call-type dropdown.
- Live Voice status: hardened WhatsApp-style full-screen UI is implemented and Android 16 emulator-verified. The screen has Night avatar/status/duration, dark patterned call background, bottom rounded tray, real Speaker/Mute controls, More status panel, End control, Azure Voice Live / Azure OpenAI Realtime protocol alignment, input transcription, semantic VAD/barge-in, Azure/OpenAI voice typing, and Night capability routing.
- Live Voice evidence: run `35622088876`, job `106410050196`, artifact `night-integrated-live-voice-evidence`.
- Current task state: fully green hardened Live Voice UI checkpoint established.
- Exact next step: perform a real provider-backed Live Voice conversation test (microphone → Azure → AI audio/transcripts → same Night chat), then test physical-device audio routing on Samsung.
