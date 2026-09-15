Slumber 0.8.6 production merge checkpoint
Date: 2026-09-15
Base: verified 0.8.5 frozen-scope source reconstructed from exact 0.8.5 source artifact + .ci/slumber_085_final_scope final patch.
Target branch: pianohub-ci only.

This patch reconciles the production/fake-state purge with the verified frozen-scope feature set instead of deleting working Songs/XML/Open-in-Slumber features.

Included:
- Remove fake day streak and fake practice-method reward state.
- Replace raw MIDI-number feedback with note names.
- Remove fake Song Studio/audio conversion surface and its persisted state while preserving Official Songs, Slumber Originals, My XMLs, XML import and Open-in-Slumber.
- Rename the default beginner piece to C major warm-up.
- Remove prototype/personalization promises from Learn/Progress copy.
- Remove unavailable instrument rows from production pickers.
- Piano quick instrument picker shows only playable installed/bundled sounds; downloads remain in Sounds.
- Sounds supports independent concurrent pack installs and shows all CulturalCatalog traditions separately from playable sounds.
- Restore Android 8+ minimum (minSdk 26).
- Test version bumped to 0.8.6 / versionCode 18.

Pre-build checks:
- Pure Kotlin learner core compilation: PASS.
- Fake/prototype state grep: PASS.
- Gross Kotlin brace-balance scan: PASS.

This checkpoint is intended for Android compile/unit-test/APK verification in GitHub Actions.
