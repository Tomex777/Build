# Humanoid appearance validation

App source tested: `c0ba9472655c0310ca54a3104d5fbab9bf359b4f`.

The focused emulator workflow passed on Android API 26 and 36:
https://github.com/Tomex777/Build/actions/runs/37048900169

It verifies the neutral body renders on first load, height and all four appearance controls, a head joint rotation, and exact pose/transform persistence after reopening. The rig contains 163 joints; the appearance shapes are body fat, muscularity, pointed ears, and ear size. Screenshots were reviewed after both runs.

Real emulator screenshots are retained in `.github/mise-previews/c0ba9472655c0310ca54a3104d5fbab9bf359b4f/`:

- `humanoid-before.png`: API 26 before edits.
- `humanoid-appearance.png`: API 26 height and appearance edits.
- `humanoid-posed.png`: API 26 head tilt after appearance edits.
- `humanoid-api36-posed.png`: same flow on API 36.

API 36's software emulator renders the body and ground brighter than API 26. Rendering remains visible on both, but lighting consistency needs further work and physical-device validation. This check does not establish every joint deformation or every combination of shape extremes.

Hair style/color, body presets, trees, and articulated bicycles/vehicles remain pending. The release candidates use temporary QA signing; permanent production signing is not part of this update.

The complete Android API 26 editor regression passed, including model import, transforms, direct posing, IK, attachments, timeline edits, export, and fresh-process save/restore. Unit/build and release-install checks passed on both APIs. API 36 completed the normal editor flow but its emulator disappeared during final instrumentation; that failed job was rerun. Track the final regression result at:
https://github.com/Tomex777/Build/actions/runs/37048900258
