# Mise completion audit — 2026-10-01

The production workflow is not yet fully accepted. Build success is not completion.

## Evidence reviewed

- API 36 emulator screenshots from run 36798235895: browser, viewport, transforms,
  hierarchy, Add, inspector, FK elbow, IK, and second-character selection.
- API 26 run 36821120856 failed switching from Pose to the second character's
  hierarchy entry. Its failure screenshot shows the viewport, not the hierarchy;
  subsequent test scrolls are camera gestures. This is not an emulator-startup failure.
- The older screenshots show rendered geometry and some arm deformation, but joint
  markers are displaced and the two-character screenshot does not visibly establish
  independent actors. These are insufficient acceptance evidence.

## Corrections

- Stable camera forwarding: SceneView 3.6 retains its initial camera manipulator in
  its frame coroutine. Project reframing must update that retained binding, otherwise
  saved camera values and projected handles diverge from the rendered camera.
- Allocate unique rig IDs before resolving parent references. Duplicate names must
  not bind a child's parent to the first namesake.
- Reduce the default pose controls and keep reset controls in the axis row.
- Explicitly close pose controls in the two-character smoke flow.
- Restrict pose gestures to joint handles; empty viewport gestures must remain camera
  gestures. Clean View closes pose controls.
- New scenes receive the existing studio light setup.

## Remaining acceptance gates

- Inspect current API 26 and API 36 screenshots after the camera correction.
- Prove unobstructed before/after elbow skin deformation, undo/redo, reset, and exact
  persisted restoration; verify independent actors are visibly separated.
- Verify joint handles align with the mesh while reframing and switching cameras.
- Schema 5 adds persistent bone attachments, hierarchy controls, and frame-by-frame
  Filament joint following. New runtime proof must verify movement and fresh-process restoration.
- Test portrait/landscape, edge gestures, pause/resume, and missing assets.
- Release signing secrets were absent in the reviewed CI run. QA-signed artifacts
  must not be described as production-signed releases.

Animation settings must not recreate a ModelNode: SceneView destroys its native root on
disposal. Keeping the loaded node alive is necessary for transforms and independent actors.

## Current runtime proof — commit 9d09c04

Run 36864777833 passed unit/build, API 26 and API 36 renderer workflows, and API 36
release installation. Reviewed real screenshots show independent character roots, actual
skin deformation, a hand-attached prop following arm rotation, and fresh-process restoration.
The saved scene and native joint-follow logs retain both independent poses and the attachment.

Visual review still rejected the XY floor strips and the oversized cube obscuring the initial
FK comparison. The next revision fixes the receiver geometry to XZ, normalizes model origins
at the ground, delays first-frame reporting until nodes have rendered multiple frames, and
scales the test prop before the FK proof. It also adds explicit black-viewport rejection,
landscape/resume/empty-scene/light/camera evidence and real Android instrumentation execution.

## Continuation acceptance — 2026-10-01 evening

CI #201 was not a compilation or startup failure. API 26 reached restored, non-black
geometry but the empty viewport lacked accessibility semantics. API 36 retained a
real hand attachment and changing native joint positions but lost rapid X-scale edits.

Commit 8178437 (CI #202) passed build and release installation. API 26 additionally
rendered a SAF-imported humanoid and bent its elbow; the test then stopped because
Android 8 toybox lacks `unlink`. API 36 still missed rapid scale input. The next
revision checks each numeric decrement, uses portable removal, keeps numeric edits
scoped to their actor, and retains all exact attachment/pose/persistence assertions.

The release gate now opens the real editor and requires loaded geometry, a frame,
and nonuniform viewport pixels on both API levels. Shadows are enabled on API 26
independently of the old post-processing shader workaround. Multi-actor evidence
now frames the whole scene. These changes require fresh runtime acceptance.

A permanent private production signing identity has been created and backed up
separately from this repository. The connected GitHub tools cannot configure
repository signing secrets. No production-signed APK has yet passed installation;
QA signing and production acceptance remain explicitly separate.
