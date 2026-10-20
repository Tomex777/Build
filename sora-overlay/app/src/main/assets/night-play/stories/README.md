# NIGHT Stories

`engine.js` is independent of prose and rendering. `player.js` loads `/stories/last-call.json` by default, or `/stories/<slug>.json` from `?story=<slug>`. A new story supplies data; it does not require branching logic inside the player.

A story defines `id`, `revision`, `title`, `author`, `start`, typed `initial` variables, `chapters`, discovered `knowledge`, `items`, `endings`, `recap` rules and `nodes`. Every node has `beats` and either `choices` or an `ending`.

A choice has a unique `id`, visible `label`, destination `to`, optional `when` condition and `effects`. `continue: true` denotes pagination, so it is not presented as a consequential decision in the journal. Conditions support `all`, `any`, `not`, typed variable comparisons, inventory, knowledge, witnessed memory and event tests. Effects support `set`, numeric `add`, `gain`, `lose`, `learn`, character `remember` and `event`. There is no evaluated code in story data.

A beat has `text`, optional `when`, `pause`, `delayBefore` and `tone`. Paragraphs occupy their final positions before ink is revealed. Delays accumulate; an authored pause cannot be bypassed by a later paragraph starting first. Tap reveals the passage and briefly guards against the same interaction selecting a choice. Reduced motion and Off settle all text immediately. All rendering uses text nodes.

Every playthrough owns its variables, inventory, knowledge, character memories, decision history, actual passage text, reading position and chapter snapshots. Forking uses the state at the chapter entrance and never copies later discoveries backwards. Completed chapter reading uses the prose actually visited in that run. Checkpoints store lengths and state, avoiding repeated copies of the whole manuscript. Endings discovered across the browser's playthroughs are the only aggregate statistic shown.

Saves use `night-story:<id>:v3`. They are browser-local. Loading does not delete malformed data; the player retains a recovery copy before replacing an unusable save. The previous short edition remains at `short-edition.html` with its old key untouched. Cross-device synchronisation and population-wide rarity require a later authenticated backend; neither is simulated.

## Last Call source and checks

Edit the Markdown and branch data in `book/`, and the decision/effect map in `book/compile.py`. Compile with:

```
python night-play/stories/book/compile.py
node night-play/stories/story-contract.cjs
```

`last-call.json` is the generated runtime content. Its word count counts authored narrative once across branches; it does not count editorial notes or duplicate renderer output. The engine test separately measures actual complete reading routes.

The browser workflow runs `combined-smoke.cjs` in Chromium on mobile and desktop and uploads reader, journal and ending screenshots. It exercises real controls for reading, saves, chapter replay, discovery and the three endings. `book/EDITORIAL.md` records the craft and mythology sources, voice rules and continuity commitments.

## Previous / Continue (permanent choices)

Readers can use **Previous** and **Continue** to revisit already-read consecutive prose passages. A revisit displays the exact recorded text immediately and never invokes narrative choices or passage effects. The reader stores the live node, inventory, resource values, skill progression and irreversible decision ledger unchanged; reloading while viewing a prior page resumes the live frontier.

The rewind limit is the passage **after** the most recently committed non-pagination decision. Before a decision is chosen, previous prose remains accessible; after choosing, its source passage and earlier pages are inaccessible through Previous. The decision itself is never re-offered. Existing chapter replay remains an explicitly **separate** fork, not a way to undo a current run.

Pagination-only `continue: true` choices are represented by the same Continue button, not a duplicate choice row. At a real choice point Continue is absent; the choices remain the only way forward. Previous is absent where there is no safe recorded page. The shared engine supplies the read-only window, so other authored NIGHT Stories inherit the behavior without changing their manuscripts or saves.

## Revision 4: identity, progression and presentation

The default setup has exactly two text inputs: `firstName` and `surname`. Names are normalised to NFC and whitespace is collapsed, with a 60-character limit per name. Ordinary Unicode letters/marks, spaces, periods, apostrophes and hyphens are supported. Markup/control characters are rejected. Templates `{{firstName}}`, `{{surname}}`, `{{fullName}}` and `|upper` resolve to strings; the UI only inserts text nodes. Identity belongs to `state`, so chapter checkpoints and forks preserve it automatically.

`rules.attributes` declares each attribute’s title, description, starting level and cap. This story starts all six at 1, with four points required at setup, a cap of 5 and three later earned points. `allocate(story,run,{observation:1})` returns an independent run and rejects overspending, negative/fractional increments and cap violations. Available points can be spent from Character. No level or stat panel occupies the reading column.

`rules.resources` declares starting values, maxima, notification text and optional `failureNode`. Resources are story-specific. Return hearts are this story’s meaning; a future story may assign the same UI a different meaning. Multi-page failure passages carry a `failureGroup` so a zero resource does not loop to the first failure page. Ordinary injury remains a distinct story variable.

`rules.abilities` declares earned disciplines, level descriptions, costs and use thresholds. Two distinct practices raise a skill to level 2; four raise it to level 3. At most two disciplines can be discovered in a run. A late-learned discipline has fewer opportunities to reach mastery. `ability` discovers one; `practice` counts a unique authored use. There are no powers in the opening state. Ability descriptions show only the current earned level.

Conditions add `attribute`, `ability`, `resource` comparisons and `abilitySlots` to the existing inventory/lore/memory/event/variable conditions. `when` hides choices that are not part of the current narrative context. `require` leaves a stat/ability choice visible but unavailable, with an authored `requirement` explanation. Critical costs also have `risk` text. Every nonterminal passage retains an unconditional fallback; the finite graph plus that invariant guarantees a way to finish for every build.

New effects include `points`, `resource`, `ability`, `practice` and `announce`. Effects can have a `when` condition and a unique `once` ledger key. Reward accounting is independent of render/animation state. Entering a visited passage does not pay its effects again, and repeated choice applications cannot repeat ledger-protected rewards. A fork owns its snapshot and its ledger; it shares no hearts or progression with another run.

Cinematic beats are authored in story data:

```json
{"type":"dialogue","speaker":"???","text":"An unfinished line—","pause":450}
{"type":"interruption","text":"The line goes dead.","pause":500}
{"type":"reveal","speaker":"The witness","revealName":"Thoth","text":"A reply.","revealAfter":620,"pause":720}
{"type":"notification","kind":"restore","text":"One heart restored","detail":"An authored reason."}
```

Dialogue and events have their own cadence and speaker display; ordinary narration retains scattered ink. The DOM reserves all text and both reveal-label sizes before animation. Tapping/Space/Enter settles the passage and applies a brief choice guard. Choice buttons do not inherit the reveal tap. Reduced motion and animation Off settle all presentation immediately. Audio remains opt-in. Current presentation events and their revealed/ready state are saved so reloading cannot reapply a reward.

### Compatible saves and chapter reading

The local-storage envelope/key remains schema/key `v3`; run content revision is now 4. Revision 3 saves migrate in place with default Sam Mansour, all attributes at 1, four available points and three return hearts. Existing learned practical training can grant Scribe Seal level 1 from that run’s discovered routes. Later discoveries are not silently granted. Identity/resources/progression/ledger are also defaulted inside each old checkpoint, with original inventory, flags, relationships and memories preserved.

`pagination-v3.json` fixes paragraph boundaries for all established passage IDs. `legacy-text-keys.json` maps hashes of old recorded paragraphs to their revised text, so migration preserves the actual conditional paragraphs read rather than evaluating old pages against a future state. Hashes are not authentication; they are lookup keys for known authored prose. An old playthrough already past the new disclosure can read an optional sealed-record interlude from Character and return to its prior passage/ending. A normal chapter replay uses the new narrative in an independent run.

The failure outcome does not count as a fourth discovered full ending. The three full endings remain release, sever and keeper, with specialised knowledge/scribe/spirit/ordinary-return variations and conditional consequences. All counts are local to this browser. No authenticated account-wide XP grant is exposed by the story integration; no account XP or global rarity is fabricated.

### Editing and verification

`book/revision-scenes.json` contains new prose, dialogue cues, choices and scene effects. `book/enhancement.py` compiles the generic rules and connects those scenes to the existing manuscript; it does not add story-specific branching code to the reader. `endEffects` applies on the last page of a scene, after earlier reveal pages have been reached. Add a separate scene when expanding an old pagination boundary instead of renaming a v3 passage.

The engine contract checks graph references/termination, all 126 starting point allocations, full endings, real attribute/ability solutions, delayed memories, independent saves, Unicode names, invalid input, old-save migration, one-shot restoration and zero-return failure. Chromium checks run the combined NIGHT Play navigation on 390px and 1280px viewports, use the actual setup/sheet/choice/save/replay controls and capture important presentation states. The workflow artifact is `last-call-book-reader`.

Reader atmosphere, character-sheet sections, interlude labels and the short-edition link are also story data. New inventory items must have authored display labels. Fare-refusal routes use separate service-stair prose and meet the witnessed route at the payphone; they do not place the group back aboard a streetcar it left.
