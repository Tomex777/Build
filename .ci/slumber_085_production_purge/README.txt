Slumber 0.8.5 production-copy / fake-state purge
Date: 2026-09-14
Baseline: exact Slumber 0.8.5 source from workflow artifact run 34645245543
Target branch: pianohub-ci only

Purpose
- Remove user-facing prototype/developer copy and fake states from the current production baseline.
- Remove the fake audio Song Studio pipeline from the shipped navigation/surface until a real converter is wired.
- Remove the fake day-streak reward because the baseline did not track calendar days.
- Remove unavailable instrument rows from the instrument picker.
- Allow independent instrument-pack installs rather than one global UI lock.
- Remove duplicate Piano controls from the instrument sheet.
- Replace MIDI-number feedback with note names (for example C4).
- Replace demo-facing 'First Melody' wording with an accurate C-major warm-up label.
- Tighten Home, Progress, Settings and Piano copy so it reads as product UI rather than implementation commentary.

Static checks performed
- Pure Kotlin learner core compiles with kotlinc: PASS.
- No references remain to SongsScreen, selectedSongName, showPipeline, PipelineRow or Prepare lesson: PASS.
- No user-facing matches remain for placeholder/prototype/not-available/coming-soon/day-streak/fake-sound wording: PASS.
- No streakDays/effectiveMethods references remain in app source: PASS.
- Delimiter counts for all modified Kotlin files are balanced: PASS.

Important
- SOURCE-ONLY. No Gradle invocation, APK build, emulator, Codespaces or GitHub Actions run.
- This purge patch is generated against the exact 0.8.5 baseline artifact. It is a production-safety patch and should be reconciled with later unbuilt song-library work rather than treated as a replacement for that work.
