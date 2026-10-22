# Agent 3 — Script Studio: remove Editor tab and phantom first-file selection

**Mission:** File browsing uses exactly **three** top-level tabs: **Files / ENV / API**. The file editor is an inner route opened by tapping a script or creating/importing one, never a top-level tab and never a reason to silently select the first file.

## First patch already present in recovery branch (unverified)

- `ScriptStudio.kt`: `StudioPage.EDITOR` stays an internal screen state but is filtered from top tabs; Files stays the active top-level section while editing; opening plain Studio with no `initialProjectId` starts with null project/path; project refresh with null preferred project no longer silently chooses the first project.
- `ScriptStudioFileCreationTest.kt`: replaces two calls that clicked the now-removed Editor tab with real file navigation; adds initial unselected test. **Run tests and correct any failures.** Do not count code pushed as evidence the fix works.

## Continue work

- Audit all usages of `StudioPage.EDITOR` and old `script_tab_editor` selectors in tests and command flows. Explicit deep link `initialProjectId` must still open or focus the intended project/file; plain opening must not pre-highlight an unrelated file.
- Files tab must be a clear return path from internal editor; opening different files must save the prior draft or block transition with a meaningful recoverable error. Preserve existing debounced save + explicit write-failure guard.
- Selecting a folder should expand/navigate the folder without **automatically editing main.js** unless user taps its file. Distinguish “folder focused” from “file opened,” ensure selected highlight matches an intentional user choice. Reevaluate project selection logic if file creation or import inadvertently highlights arbitrary entries.
- Keep New File, Folder, Import and ZIP flows; verify file types JS/TS/Python/Lua/HTML/CSS, Create, Rename, Move, Delete, Search, Preview and Run. No duplicate edit action or unintended source mutation just by browsing.
- Keep ENV/API accessible from both file list and internal editor, including return without draft loss; avoid unnecessary “Editor” button or tab elsewhere. Buttons, field styles, and safe insets follow ABR.
- Functional UX tests on API26 and API36 plus orientation/IME. Compare screenshot with user: four top tabs → **three only**. Unselected file row must use same appearance as other unselected rows.

## Acceptance cases

Cold open with multiple scripts → no entry highlighted, preview disabled. Tap `canvas-snake.js` → actual code editor, saved file remains independent. Switch Files → file list; ENV → secrets intact; API → actual API docs. Create file → editor with correct type. Attempt disk-write failure → draft retained, switching blocked. Delete active project → no first-project fallback. Trigger file deep link → correct file. Rotate, app force-stop/restore → no corruption.

## Deliverables

Test output, before/after Studio screenshots, code diff, debug and owner-signed/upgrade gate status. Do not report “fixed” while emulator tests are unrun or red.
