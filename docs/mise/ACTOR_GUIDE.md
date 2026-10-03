# Mise actor controls

Open Add and choose a starter actor, or import a self-contained GLB/glTF model. Starter models ship inside the application.

| Actor | Controls |
| --- | --- |
| Humanoid | Height, body fat, muscularity, pointed ears, ear size; none/short/bob/afro hair and five hair colors; joint posing and IK |
| Bicycle | Front steering, front/rear wheel rotation, pedals, and whole-bike lean |
| Car | Separate front steering joints, four rotating wheels, and two outward-opening doors |
| Tree | Position, rotation, scale, visibility, duplication, and hierarchy parenting |

Select an actor and open Inspector. Humanoid appearance rows appear after its model has loaded. Mechanical parts also appear in Pose. Each part has bounded rotation controls and a reset action; steering and wheel spin use separate joints. Vehicle part positions can be keyed in the animation timeline.

Camera → Frame selected shows the selected starter actor in full. Frame scene fits all visible actors and accounts for the larger starter vehicles and trees. Use Move, Rotate, and Scale for scene composition, and clean view or PNG export for an unobstructed artist reference.

Save preserves actor transforms, humanoid appearance, joint poses, and animation keys. Undo/redo applies to edits. Imported characters keep their own rigs and morph targets; the bundled humanoid's appearance controls are specific to that model.

These starter assets are stylized reference geometry. Vehicle controls articulate the model; movement can be authored on the timeline. The tree is scenery.
