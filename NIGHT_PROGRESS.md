# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Last verified provider checkpoint: `1f7eccb998832fbb887b3ae28de1856082b3d501` — Android 16 provider instrumentation passed.
- Current branch checkpoint: `27dc69c9642237855d04725c6127555c9cf2289b` — expanded provider HTTP failover coverage.
- CI state:
  - Night Integrated Regression `35610858005`: PASS
  - Android 16 provider job `106371486232`: PASS
  - Night Groq Key Pool `35610858539`: PASS
- Completed major systems: GROQ encrypted multi-key pool + rotation/cooldown/failover; real provider HTTP instrumentation; full-screen PDF viewer/editor derivative-copy flow; image/video regression coverage; Mihon reader regression; rich message regressions.
- Night calling rule: Night is user ↔ AI only. No Homira/person-to-person/WebRTC/FCM calling. No Calls bottom-navigation tab. The phone icon inside an AI chat starts the voice-only Live Voice conversation directly; no video-call option or call-type dropdown.
- Live Voice implementation: `NightLiveVoiceClient` with Azure Realtime / Azure Voice Live routing, launched from the chat `onCallClick`; transcripts return to the same chat.
- Current unfinished task: validate and harden the Live Voice client and its call-screen behavior without disturbing already-green systems.
- Exact next step: add focused tests around Live Voice endpoint/session selection and verify the app/build on Android 16.
