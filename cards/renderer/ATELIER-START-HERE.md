# NIGHT / Ten new designs

Ten independently composed Ichigo Kurosaki collectible fronts, numbered 01–10.
All use the same supplied illustration, a 1000 × 1600 master coordinate space,
and programmatic SVG construction. No finished card or background was produced
with image generation. No combat statistics or character-specific scenery.

Open **dist/NIGHT-Ten-Designs.html** in a current browser. It is self-contained
and works offline. Open any card to enlarge it, use the arrow keys to browse,
download its SVG, or save favorite numbers locally for later. The HTML file
does not send selections anywhere. Browser storage availability controls
whether picks survive closing the page.

The numbered PNG exports are **3000 × 4800**. Frames, lettering, engraving,
shadows, glass, foil and texture are rendered at the final output density.
The original illustration retains its source detail; a larger canvas does not
invent new artwork detail. Embedded SVGs can be rendered at other resolutions.

## The ten directions

| No. | Design | Composition and finish |
| --- | --- | --- |
| 01 | Obsidian | Open full-art crop, fine floating copper rails, black lacquer |
| 02 | Gilt | Ivory mat, warm metal engraving, inset museum title plaque |
| 03 | Spectra | Opal glass, spectral planes, prismatic inset edge |
| 04 | Monograph | Vertical name spine, offset art window, editorial stock |
| 05 | Exoskeleton | Asymmetric machined titanium, cut metal nameplate |
| 06 | Vermilion | Borderless scarlet lacquer, gestural line engraving |
| 07 | Archive | Smoked glass case, archival label, restrained sans type |
| 08 | Cutline | Diagonal black and ivory planes, pink foil edge |
| 09 | Palatine | Top title, stepped brass frame, architectural gold fluting |
| 10 | Stratum | Mineral contour engraving, diagonal glass, etched silver |

## Rebuild

Requires Node 22+ and Python with Pillow for the presentation boards.

```sh
npm ci
npx playwright install chromium
node bin/atelier-render.mjs examples/characters/ichigo.json dist/atelier 3000
node bin/atelier-gallery.mjs examples/characters/ichigo.json dist/NIGHT-Ten-Designs.html
python bin/atelier-present.py dist/atelier dist/atelier-views
node bin/atelier-verify.mjs
node --test tests/*.test.mjs
```

Use `NIGHT_BROWSER_PATH=/absolute/path/to/chromium` to select an installed
Chromium. The optional fifth argument of `atelier-render.mjs` is a comma
separated list of design IDs. Output width supports 160–4000 pixels. Its
height is 1.6 × width. Outputs are published only after full pixel decoding.

The gallery and rendering CLI accept another character JSON as the first
argument. The layouts remain generic; no conditional code checks for Ichigo
or Naruto. Supply the name, subtitle, image path, dimensions, hash, author,
source and actual artwork rights in a profile. Transparent hero art with a
strong upper-body silhouette works best with these compositions. A different
pose still needs visual review before unattended publication.

```js
import {renderAtelierCard} from './src/atelier.mjs';
import {loadCharacterAssets} from './src/character-assets.mjs';
const assets = await loadCharacterAssets(characterProfile);
const svg = renderAtelierCard(characterProfile, assets, {design:'spectra'});
```

`croppedArtworkDimensions`, when supplied in artwork metadata, describes the
visible content before transparent bottom padding. The renderer calculates
the lower fade from that content height, avoiding flat cutout edges. It never
distorts the supplied illustration's aspect ratio.

## Source organization and preservation

New work is additive: `src/atelier.mjs` and `bin/atelier-*.mjs` /
`bin/atelier-present.py`. All 40 pre-existing source, CLI and profile files are
preserved byte for byte. `qa/atelier-preserved-baseline.json` records their
hashes. The original renderers and completed taller Nocturne/Tidelight variant
are included. `README.md`, `DEPTH-EDITIONS.md` and `PORTRAIT.md` document those
earlier editions. Existing archives and exports were kept separately.

`src/discovery.mjs` and `bin/artwork.mjs` retain the automated high-resolution
art discovery workflow. It queries the official Krita mascot gallery and the
Wikimedia Commons API, checks eligible license metadata, decodes real image
dimensions, records hashes and source attribution, and rejects unknown or
restricted licenses. Discovered candidates require composition review. The
Ichigo and Naruto official references are separate from that open-license
catalog; discovery does not grant rights to licensed characters.

## Artwork and fonts

Ichigo: official BLEACH: Soul Resonance illustration.
https://www.bleach-soulresonance.com/

©Tite Kubo/Shueisha, TV TOKYO, dentsu, Pierrot. ©Nuverse Pte. Ltd.
All rights reserved. These are personal design prototypes; the artwork is not
commercially cleared by this source package. Rendering code is MIT. That
license does not cover the character artwork. Every exported SVG retains its
provenance and rights metadata. Font licenses and all other artwork terms are
included in `assets/licenses` and their source profiles.

## Verification

Three visual passes, including two all-ten render passes and focused revisions.
The final pass was checked from actual Chromium renders at 3000 × 4800, with
native-pixel detail crops and 320 × 512 phone proofs. See
`qa/atelier-final/review.md`, `verification.json` and `exports-verification.json`.
The browser verifier covers all 20 Ichigo/Naruto × design combinations for
title bounds, valid unique SVG paint references and artwork proportions. It
also checks the gallery's enlargement, saved picks, navigation and mobile
layout. These automated checks do not replace visual judgment of new art.
