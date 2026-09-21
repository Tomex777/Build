# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Last fully verified commit: `e284a8812cf0796e4d4d17868802479831ab0da3`
- Verified CI:
  - Night Groq Key Pool `35616280503`: PASS
  - Night Integrated Regression `35616280513`: PASS
  - Android 16 build: PASS
  - Android 16 provider HTTP failover: PASS
  - Android 16 video: PASS
  - Android 16 message blocks: PASS
  - Android 16 image editor: PASS
  - Android 16 PDF editor: PASS
  - Android 16 Mihon reader: PASS
- Completed major systems: GROQ encrypted multi-key pool + rotation/cooldown/failover; real provider HTTP instrumentation; full-screen PDF viewer/editor derivative-copy flow; image/video regression coverage; Mihon reader regression; rich message regressions.
- Night calling rule: Night is user ↔ AI only. No Homira/person-to-person/WebRTC/FCM calling. No Calls bottom-navigation tab. The phone icon inside an AI chat starts the voice-only Live Voice conversation directly; no video-call option or call-type dropdown.
- Live Voice status: Azure Voice Live / Azure OpenAI Realtime protocol aligned, input transcription enabled, semantic VAD/barge-in retained, Azure/OpenAI voice types distinguished, and Live Voice routed through Night capability selection with selected-chat-model-first behavior where supported.
- Current task state: green checkpoint established.
- Exact next step: continue hardening real Live Voice behavior on-device without disturbing the verified provider/media/PDF/Mihon/message systems.
