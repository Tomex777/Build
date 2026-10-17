# NIGHT — restored Nocturne and Tidelight

Nocturne returns with its entire circular engraved disc removed: the dark
fill, metal rings and radial tick marks are gone. Its angular metal architecture,
engraved outer frame, subject lighting, cast shadows and typography are retained.
Tidelight returns with its existing pearl architecture. Both use the previously
requested taller 5:8 format and omit the old bottom-right faceted emblem.

All previous source files are retained unchanged. The new adapter is
`src/restored.mjs`; it calls the preserved portrait/depth renderer and removes
only the complete circular-disc SVG group. Structural guards fail explicitly
if the older renderer changes. The disc is also removed on Nocturne's reverse.
Tidelight's pearl engraving is retained.

## Render another character

```sh
npm ci
npx playwright install chromium
node bin/restored-render.mjs examples/characters/ichigo.json dist/restored 3000
node bin/restored-render.mjs examples/characters/naruto.json dist/restored-naruto 3000
node bin/restored-verify.mjs
```

Use `NIGHT_BROWSER_PATH` for an existing Chromium executable. The renderer
also checks the development browser path used by the earlier source package.
Each run exports two self-contained SVGs, two fully decoded 3000 × 4800 PNGs,
two 320 × 512 phone proofs and a provenance/hash manifest. Another profile can
use the same CLI; character identity and artwork are inputs rather than changes
to the frame implementation.

```js
import {renderRestoredCard, setRestoredLight} from './src/restored.mjs';
import {characterCards} from './src/characters.mjs';
import {loadCharacterAssets} from './src/character-assets.mjs';

const card = characterCards(templates, profile).find(c => c.theme === 'nocturne');
const assets = await loadCharacterAssets(profile);
const svg = renderRestoredCard(card, assets);
// The existing normalized light controls also work on this variant:
setRestoredLight(svgElement, [0.65, 0.3]);
```

## Proposed collection structure

`examples/ichigo-collection-proposal.json` proposes eight collection tiers and
15 distinct form identities. It is a discussion draft; it changes no live bot
settings and specifies no drop rates or prices.

| Tier | Frame | Proposed Ichigo cards |
| --- | --- | --- |
| Common | Vanguard | Human; early Substitute Soul Reaper |
| Rare | Ivory | Original Shikai; Fullbring |
| Epic | Voltage | Original Bankai; Hollow mask |
| Legendary | Crimson | Full Hollow / Vasto Lorde; Fullbring-enhanced Bankai |
| Mythic | Emberglass | Dangai; True Shikai |
| Secret Rare | Obsidian | Mugetsu; True Bankai |
| Celestial | Tidelight | Horn of Salvation; Hell Verse film edition |
| Transcendent | Nocturne | Blood Chains |

These are collection placements, not an official power ranking. The proposed
tiers extend the previous six-tier discussion without moving Obsidian out of
Secret Rare. More than one form can belong to one tier. A character does not
need to have a form at every tier. Alternate artwork editions can be introduced
later through an explicit collection rule.

For unattended rendering, resolve the exact character/form ID first, then its
verified artwork record, then its assigned design. `ichigo/blood-chains` must
resolve to artwork depicting Blood Chains. Never relabel the included original
Shikai illustration as a different form. The proposal marks unavailable artwork
as `requires-form-matched-artwork`, rather than silently substituting it.

Hell Verse is a separate film continuity. Blood Chains is recorded separately
from True Bankai, following the official anime terminology page at
https://bleach-anime.com/keyword/ (entry 血鎖の一護). The existing Shikai reference
is the sole artwork used in the restored pair; a full form collection has not
been illustrated or rendered by this change.

## Resolution, rights and review

The PNG canvas is 3000 × 4800. Frame, engraving and typography render at that
density. The included official illustration retains its original detail and
rights reservations; no AI images or AI upscaling are used. See `ICHIGO.md`.
The software MIT license does not grant rights to sell BLEACH illustrations.
The existing open-license discovery and provenance workflow remains available
in `src/discovery.mjs` and the main README.

The actual pair was inspected after the disc removal, then exported at full
resolution and inspected with native-pixel detail crops and 320 × 512 phone
proofs. These phone proofs do not test WhatsApp upload/compression. Verification
compares parsed SVGs against the preserved renderer: every node outside the
requested disc removal must match, including artwork, typography and frame.
Both characters, both finishes and both sides are checked, together with all
46 original source hashes. See `qa/restored-final/verification.json`.
