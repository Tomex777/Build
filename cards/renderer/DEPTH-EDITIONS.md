# NIGHT — Depth Editions 2.0

Two complete, reusable designs: **Nocturne** and **Tidelight**. The new renderer
uses three depth planes, character alpha masks, cast and contact shadows,
fine edge lighting, sculpted metal borders, optical engraving, selective
foil and embossed type. The source illustration remains a separate asset.
The finish, shadows and parallax respond to the same light in the viewer
and the exported SVG.

## The original is preserved

`NIGHT-Cards-Original-Preserved.zip` is a verified copy of the previous source
release. The original working directory was not modified. Its source hashes
are recorded in `original-preservation.json`. The earlier renderer also
remains available in this project as `src/index.mjs`; the new renderer is
`src/depth.mjs`. No previous design was deleted.

## Open and render

Open `dist/NIGHT-Depth-Editions.html` directly in a modern browser. It works
offline. Select the two finishes, move the light, turn the card, and save a
4K PNG or the complete scalable SVG. Keyboard arrows move the light; Home
resets it. Reduced-motion preferences disable parallax and card tilt.

```sh
npm ci
npx playwright install chromium
npm run depth:build
npm run depth:render
npm run depth:8k
npm run depth:16k
npm test
node bin/depth-verify.mjs
```

The CLI defaults to the included Naruto profile. Supply another profile:

```sh
node bin/depth-export.mjs examples/characters/your-character.json dist/yours 6000 png
node bin/depth-export.mjs examples/characters/your-character.json dist/yours 12000 jpeg
node bin/depth-studio.mjs examples/characters/your-character.json dist/yours.html
```

An existing Chromium can be selected with `NIGHT_BROWSER_PATH`. No browser
download occurs while rendering. Node 22.12+ is recommended.

| Export | Exact dimensions | Format |
| --- | --- | --- |
| Viewer / 4K | 3000 × 4200 | PNG |
| 8K master | 6000 × 8400 | PNG, transparency retained |
| 16K large master | 12000 × 16800 | JPEG, quality 97, 4:4:4 chroma |
| Vector master | 1000 × 1400 viewBox | Self-contained SVG |

The large renderer draws eight vector tiles at the requested density and
joins them without scaling a smaller card bitmap. Complete files are fully
decoded, then atomically published. Width, height and tile dimensions are
checked. The SVG remains available for another raster size or print workflow.

## Change the character, keep the design

Use one character JSON profile as in `examples/characters/naruto.json`.
The same profile feeds both finishes. Supply a high-resolution transparent
PNG or WebP cutout, its actual dimensions, creator, source, rights and hash.
The character edge, rim light and shadows follow the supplied alpha channel;
they are not manually drawn around Naruto. Opaque rectangular artwork will
not create a character cutout by itself.

```js
import {renderDepthCard, setDepthLight} from './src/depth.mjs';
import {characterCards} from './src/characters.mjs';
import {loadCharacterAssets} from './src/character-assets.mjs';

const card = characterCards(templates, character)[0];
const artwork = await loadCharacterAssets(character);
const svg = renderDepthCard(card, artwork, {light:[0.28,0.2], foil:1});
// In a browser, update the existing SVG without regenerating the artwork:
setDepthLight(svgElement, [0.8,0.3]);
```

Optional `depthSubject: {x,y,width,height}` uses the same 1000 × 1400 design
coordinates. Ordinary character swaps need only the profile and artwork.
Names automatically fit a safe text width, and identity text is XML-escaped.

## Artwork discovery and rights

The original automated discovery, license validation, high-resolution
acquisition and provenance pipeline is retained. See the main README and
`src/discovery.mjs`. Unknown or restricted licenses are not automatically
accepted into its open-license catalog.

The Naruto reference is the same official illustration as the previous
cards: Masashi Kishimoto, © SHUEISHA, from
https://naruto-official.com/en/about . Its native size is **1576 × 1884**.
The frame, typography, engraving and material effects are rendered at the
full export resolution; the illustration retains its original detail.
No AI image generation or AI upscaling was used.

Naruto artwork is not covered by the MIT software license and is not a
commercially cleared image. The example demonstrates the rendering system;
commercial character products need appropriately authorized artwork. The
existing Kiki assets retain their original CC BY-SA 4.0 terms. Fonts are OFL.

## Validation

Actual card images were inspected after each design change, including full
cards, native-resolution detail crops, and small viewing proofs. The browser
checks both finishes, offline behavior, interactive light/export agreement,
parallax, foil, reverse, 4K export, touch layout and reduced motion. Automated
checks also cover source preservation, rendering determinism and unsafe
inputs. See `qa/depth-final/` and `docs/DEPTH-REVIEW.md`.

The 16K JPEG is a digital image. Digital foil is simulated light; the source
does not constitute a manufacturer's physical stamping or embossing plates.
