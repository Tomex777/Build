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

Hair choices now include none, short, bob, and afro, with five color swatches.
The CC0 MakeHuman system hair meshes follow the head joint and the same four
body shape targets. Source mappings, hashes, and derived texture provenance
are retained under scripts/hair-source. Appearance is persisted in schema 6;
older scenes migrate with their original bald appearance.

The catalog now also includes original articulated bicycle and car starters
and a static tree. See ACTOR_CONTROLS.md for controls and acceptance checks.
Body presets and driving physics are not part of these appearance controls.

