# Actor controls

Mise includes eight bundled starter models. In Add > Starter, select Character for Humanoid, Vehicle for Bicycle or Car, and Environment for Tree. Imported models remain available through My Assets. Bundled actors need no runtime network connection.

## Humanoid

Inspector > Appearance provides proportional height, body fat, muscularity, pointed ears, ear size, four hair choices (none, short, bob, afro), and five hair colors. Hair is fitted to the body and weighted to the head joint. Its four shape targets share the body controls. Appearance, shapes, and poses use project history and saving. Schema 5 projects migrate to schema 6 with the original bald appearance.

MakeHuman body and system hair are CC0 graphical assets. Original source files, source hashes, prepared texture hashes, and license provenance are retained in scripts/hair-source. The independent converters import no MakeHuman application code.

## Bicycle and car

Inspector > Movable parts, or Pose, exposes only supported mechanical joints. The bicycle has front steering (fork, handlebars, and front wheel), independent wheel rotation, pedals, and whole-actor lean. The car has front steering pivots, four independently rotating wheels, and two outward-opening front doors. Steering and doors have limited ranges; wheel rotation uses the axle axis. Part edits support undo, redo, saving, reopening, and pose timeline keys. These are articulated reference models; controls do not simulate driving physics.

Tree is static scenery with a trunk, branches, roots, and layered foliage. Transform it with the normal actor tools.

The tree and vehicles are original procedural geometry. scripts/build-scene-actors.py rebuilds their rigidly skinned GLBs deterministically using the Python standard library. The generator and catalog retain the provenance and CC0 dedication.

## Acceptance

The Android CI workflow builds debug/release APKs and an AAB, runs core tests, then checks the editor on API 26 and API 36. Each fresh instrumentation emulator runs either humanoid/editor tests or mechanical actor tests. Part tests require a visible geometry change and saved/reopened rotations; screenshot retrieval and viewport checks are required for acceptance. Broad editor smoke and release installation run independently.
