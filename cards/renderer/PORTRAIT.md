# NIGHT — Taller Cards

The current Nocturne and Tidelight designs in a **5:8** portrait format:
1000 × 1600 design coordinates, approximately **14.3% taller** than the previous
1000 × 1400 format. Finished PNGs are **3000 × 4800**. Artwork, typography,
materials and credits come from the same character profiles. The bottom-right
faceted emblem is removed, as requested.

The previous renderer and character profiles remain byte-for-byte unchanged.
`preserved-current-hashes.json` records their hashes. The taller variant lives
in `src/portrait.mjs`, layered over `src/depth.mjs`.

## Rebuild

```sh
npm ci
npx playwright install chromium
node bin/portrait-render.mjs qa/tall-01 1500 --compare
node bin/portrait-render.mjs dist/tall 3000
python3 bin/portrait-present.py
```

Node 22.12+ is recommended. The presentation script uses Pillow. An installed
Chromium can be selected with `NIGHT_BROWSER_PATH`. The two included characters
are example inputs; a new character can use the same renderer:

```sh
node bin/portrait-render.mjs dist/yours 3000 --profile examples/characters/your-character.json
```

```js
import {renderPortraitCard, setPortraitLight} from './src/portrait.mjs';

const svg=renderPortraitCard(card,assets,{height:1600});
// Optional browser interaction, using the existing material/depth light controls:
setPortraitLight(svgElement,[0.7,0.3]);
```

## Proportions

This is a vector layout adaptation. The framing and decorative geometry follow
the taller canvas. The character illustration is scaled **equally in X and Y**
to fill it, which makes a slightly closer crop at the sides. It is not stretched
into a taller body. Text has inverse vertical compensation, keeping the glyphs
at their original proportions. Fonts and artwork remain embedded in each SVG.

`height:1400` renders the current ratio without the emblem; `height:1600` renders
the taller comparison. Both comparison cards omit the emblem so the visible
comparison isolates the proportions. The existing base renderer can still
reproduce the original cards with their original emblem.

The legacy 8K/16K commands still target the previous 5:7 renderer. Use
`portrait-render.mjs` for this variant; its tested 4K export is 3000 × 4800.
The standalone SVG is scalable. The illustrations retain their original source
detail; no AI generation or AI upscaling was used.

## Artwork and verification

Artwork identity, source URLs, hashes and reserved rights are retained in
`examples/characters/ichigo.json` and `naruto.json`. The software MIT license
does not license these character illustrations for sale. See `ICHIGO.md`,
`NARUTO.md` and `ATTRIBUTION.md` for the applicable sources and font terms.

Actual renders were inspected at both proportions for both finishes and both
characters, then checked at 3000 × 4800 and 320 × 512. The output dimensions,
SVG structure, equal character scale, undistorted text, removal of the emblem,
and preservation of existing source files are verified in the accompanying
review records. Small images simulate phone viewing; no WhatsApp transmission
or compression test is claimed.
