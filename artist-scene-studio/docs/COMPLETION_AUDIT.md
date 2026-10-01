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
