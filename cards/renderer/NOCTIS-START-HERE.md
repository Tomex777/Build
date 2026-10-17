# NIGHT / One preserved. Nineteen new.

**01 Obsidian is the exact approved original.** 02–10 rebuild the nine rejected
directions; 11–20 add ten more. Earlier renderers and source files are retained.
The new work is in `src/noctis.mjs`, `src/noctis-assets.mjs` and `bin/noctis-*`.

Open `dist/NIGHT-Noctis-Collection.html` in a browser to compare all twenty,
enlarge a design, use arrow keys, download its SVG, and save picks locally.
The gallery works offline and does not send selections anywhere.

| No. | Name | Composition / finish |
| --- | --- | --- |
| 01 | Obsidian | Preserved original: full art, copper rails, black lacquer |
| 02 | Aurum | Open heroic crop, burnished gold, monumental serif |
| 03 | Prism | Spectral planes, selective foil, narrow prismatic edge |
| 04 | Vanguard | Large type behind and in front of the illustration |
| 05 | Carbon | Woven surface, red lacquer, tilted condensed name |
| 06 | Crimson | Fluid engraving, deep red ink, italic lettering |
| 07 | Ghost | Smoked silver and a vertical serif name |
| 08 | Kinetic | Right-shifted portrait, bold vertical name, ivory cut |
| 09 | Sovereign | Gold masthead and a lower, expansive illustration |
| 10 | Abyss | Blue-black glass, outlined block lettering, silver cuts |
| 11 | Mercury | Liquid chrome folds and reflective serif type |
| 12 | Afterimage | Offset silhouette echoes and oversized spectral lettering |
| 13 | Relic | Antique bronze engraving and centered sculptural type |
| 14 | Noir | Close portrait and bold monochrome lettering |
| 15 | Aurora | Violet/cyan waves and a silver serif title |
| 16 | Voltage | Acid light and a broad diagonal name |
| 17 | Echelon | Rising metal planes and a vertical copper name |
| 18 | Visage | Face-dominant crop and tall copper type |
| 19 | Emberglass | Transparent amber folds and italic metal lettering |
| 20 | Ivory | Pale mineral field, deep shadow, dark serif name |

## Resolution and assets

The finished PNGs are **3000 × 4800**. Each SVG is self-contained with embedded
fonts and illustration. Framing, typography, engraving, texture, lighting,
shadows, material simulation and compositing are programmed. No card,
background, texture or illustration in this collection was generated with AI.
There are no combat statistics, ability boxes or decorative seal icons.

The original Ichigo illustration contains 1154 × 1080 visible source pixels
on a padded 1154 × 1380 canvas. The large export gives the programmed design
high-resolution detail; it does not invent additional detail in the source art.
The tight Visage crop makes that limitation most visible at native pixel zoom.
Included 320 × 512 phone proofs approximate small chat viewing size. They are
not a claim about WhatsApp upload limits or its transmission compression.

## Rebuild

Requires Node 22+, npm, Chromium, and Python with Pillow for proof boards.

```sh
npm ci
npx playwright install chromium
node bin/noctis-render.mjs examples/characters/ichigo.json dist/noctis 3000
node bin/noctis-gallery.mjs examples/characters/ichigo.json dist/NIGHT-Noctis-Collection.html
python bin/noctis-present.py dist/noctis dist/noctis-views
node bin/noctis-verify.mjs
node --test tests/*.test.mjs
python bin/noctis-package.py dist/noctis-deliverables
```

Set `NIGHT_BROWSER_PATH` to use an existing Chromium executable. Raster width
supports 160–4000 pixels; height is 1.6 × width. The optional fifth argument is
a comma-separated list such as `prism,noir,visage`. Use a separate directory
for partial exports: `renders.json` describes only that invocation's selected
cards. Full pixel decoding precedes publication.

For Ichigo's Obsidian at width 3000, the CLI copies the preserved original PNG
byte for byte. Other sizes or characters use the original renderer. This source
snapshot includes that original PNG, SVG and renderer.

```js
import {renderNoctisCard} from './src/noctis.mjs';
import {loadNoctisAssets} from './src/noctis-assets.mjs';
const assets = await loadNoctisAssets(characterProfile);
const svg = renderNoctisCard(characterProfile, assets, {design:'visage'});
```

Supply a character JSON with name, subtitle, transparent artwork, dimensions,
hash, attribution and actual rights. The same twenty compositions accept
another profile; the browser verifier checks both Ichigo and Naruto. Artwork
aspect ratio is preserved. `croppedArtworkDimensions` describes visible content
before transparent padding, allowing the lower fade to follow it. Strong
upper-body poses work best. A different silhouette still needs review; the
package does not claim unattended artistic quality for arbitrary images.
The proof-board native face coordinates are calibrated for this Ichigo example.

## Discovery, rights and preservation

The existing `src/discovery.mjs` and `bin/artwork.mjs` are included unchanged.
They discover high-resolution artwork from the official Krita mascot gallery
and Wikimedia Commons, check license metadata, verify decoded dimensions,
and retain source URLs, hashes and attribution. Unknown and restricted
licenses are rejected. Acquired images remain unapproved for composition until
their rendered cards have been reviewed.

Ichigo is an explicitly selected official reference, separate from that
open-license catalog. Original source: https://www.bleach-soulresonance.com/
©Tite Kubo/Shueisha, TV TOKYO, dentsu, Pierrot. ©Nuverse Pte. Ltd.
All rights reserved. These are personal design prototypes; this package does
not include commercial artwork rights. The MIT license covers rendering code,
not the character artwork. Font licenses are in `assets/licenses`, including
the newly added Bebas Neue and Archivo Black.

All 46 pre-existing source, CLI and example files are byte-identical to
`qa/noctis-preserved-baseline.json`. The preserved Obsidian PNG SHA-256 is:

```text
d20d9d074cd8d5835dc99502314500c7da6ecb08c1ee5605dcf5460ad7fd7156
```

See `qa/noctis-final/review.md` for visual decisions and practical limits,
`verification.json` for browser checks, and `exports-verification.json` for
full-resolution decoding. Native pixel crops and actual browser screenshots
are included. Earlier edition documentation remains alongside this guide.
