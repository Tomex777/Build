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
