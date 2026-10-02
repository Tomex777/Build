# Humanoid appearance foundation

The Humanoid starter uses MakeHuman Community graphical assets under CC0-1.0,
pinned to revision a8bc2d54ff0ac92e78ff71431b1023eda42bf482. The independent
standard-library converter imports no MakeHuman application code. Source blob
hashes are checked before conversion; the full asset license is bundled in the APK.

Run `python3 scripts/build-humanoid.py` to generate the GLB. The normal fixture
fetch script runs this in every CI build. It contains a smooth 13,380-vertex body,
163 skin joints, and four position/normal morph targets: body fat, muscularity,
pointed ears, and ear size. Body helper geometry is excluded, skin weights use
the four strongest normalized influences, and inverse bind matrices preserve the
rest pose. Height changes scale the character proportionally using editor history.

Add > Starter > Characters > Humanoid exposes the asset. Inspector > Appearance
uses the discovered mesh targets. Joint posing, morph weights, and height share
the existing project persistence and undo system. Dense face/toe/finger joints
are omitted from the default viewport handles; finger controls remain selectable.

Validation includes source hashes, geometry/weight checks, size/history unit tests,
and emulator edits, joint posing, reopening, and before/after screenshots on API
26 and API 36. Screenshot inspection is required before declaring runtime success.

Remaining work: hair meshes and color, body presets and broader weight ranges,
testing combined shape extremes and all joint deformations, then articulated
bicycles/vehicles and tree assets. This is a foundation, not the completed asset catalog.
