# Anime tree scene inside Mise

This scene uses unmodified publisher-authored 3D meshes. No Blender or AI model/image
generation is used. The Android scene driver uses Mise's importer, SceneEditorState,
FilamentRigRuntime, project store and viewport to compose and pose the actors.

- HairSample_Female and HairSample_Male: pixiv / VRoid, CC0 1.0.
  Publisher confirmation: https://vroid.pixiv.help/hc/en-us/articles/4402614652569
  Pinned mirror revision e16eb187100149a315ad92c3c9968f1d5baa6c7d.
  These detailed anime samples are generic clothed avatars; not a claim that they
  are Magic Poser assets. VRM is already binary glTF; the download step changes
  only the filename extension for Mise's import flow.
- Tree: Quaternius, CC0, https://poly.pizza/m/qZtx0AHhcy.
- Bicycles: jeremy, CC BY 3.0, https://poly.pizza/m/axc03j3xKfz.
  License: https://creativecommons.org/licenses/by/3.0/. Scene changes: duplicated,
  positioned, scaled and rotated. Keep this credit with redistributed captures.
- Ground: Isa Lousberg, CC0, https://poly.pizza/m/YnEAUb15rj.

All poses are joint offsets applied by Mise; meshes and source rigs are retained.
The Android test also verifies save/reopen and editor undo/redo. Passing automation
alone does not certify the aesthetic result; inspect the actual screenshots.
