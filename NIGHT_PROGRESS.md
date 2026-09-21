# Night Progress

- Active branch: `night-groq-key-pool-ci`
- Last verified commit: `1f7eccb998832fbb887b3ae28de1856082b3d501` — Allow localhost HTTP in debug provider tests
- CI state:
  - Night Integrated Regression `35610858005`: PASS
  - Android 16 provider job `106371486232`: PASS
  - Night Groq Key Pool `35610858539`: PASS
- Completed major systems: GROQ encrypted multi-key pool + rotation/cooldown/failover; real provider HTTP instrumentation; full-screen PDF viewer/editor derivative-copy flow; image/video regression coverage; Mihon reader regression; rich message regressions.
- Current unfinished task: strengthen provider behavior coverage without changing already-green media/UI behavior.
- Exact next step: inspect `NightAiGatewayProviderInstrumentedTest.kt` and provider rotation logic, add the highest-value missing HTTP-level failover/cooldown test, then run provider + integrated regression.
- Later high-priority production task: real two-device/background incoming-call ringing and timely missed-call reliability.
