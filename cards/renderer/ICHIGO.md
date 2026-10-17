# NIGHT — Ichigo Kurosaki

The same Nocturne and Tidelight Depth Editions, using Ichigo's Shikai artwork.
The renderer, framing, typography, palettes, material effects, shadows and
character rectangle are unchanged. Only a character profile and artwork asset
were added. Naruto and the preserved original collection remain intact.

Open `dist/NIGHT-Ichigo-Depth.html` for the self-contained offline viewer.
Move the light, turn the card, or export a 3000 × 4200 PNG / complete SVG.

## Rebuild

```sh
npm ci
npx playwright install chromium
node bin/depth-studio.mjs examples/characters/ichigo.json dist/NIGHT-Ichigo-Depth.html
node bin/depth-render.mjs examples/characters/ichigo.json dist/ichigo-depth 3000
node bin/depth-export.mjs examples/characters/ichigo.json dist/ichigo-depth 6000 png
node bin/depth-export.mjs examples/characters/ichigo.json dist/ichigo-depth 12000 jpeg
```

Node 22.12+ is recommended. An installed Chromium can be selected with
`NIGHT_BROWSER_PATH`. See `DEPTH-EDITIONS.md` for the shared API and design.

## What changes for a character

`examples/characters/ichigo.json` contains the name, identity and artwork
provenance. `assets/art/ichigo-official.png` supplies the transparent character.
The existing CLI binds this profile to both templates. There is no
Ichigo-specific rendering code, traced shadow or custom framing.

The official source illustration is 1920 × 1080. Its transparent side margins
were trimmed to 1154 × 1080 and 300 transparent rows were added below it,
producing a 1154 × 1380 asset fitted to the existing character rectangle.
The illustration pixels were not resampled, recolored, generated or upscaled.
Preparation and source hashes are recorded in the JSON profile.

The 8K PNG canvas is 6000 × 8400. The 16K JPEG canvas is 12000 × 16800. The
frame and type render at those densities; the original artwork retains its
source detail. These sizes do not imply an 8K/16K native illustration.

## Artwork source and rights

Official BLEACH: Soul Resonance character artwork:
https://www.bleach-soulresonance.com/

©Tite Kubo/Shueisha, TV TOKYO, dentsu, Pierrot. ©Nuverse Pte. Ltd.
All rights reserved. This user-requested character reference is separate from
the open-license discovery catalog and is not commercially cleared by this
package. The rendering code's MIT license does not license BLEACH artwork.
Original Naruto/Kiki assets retain their own recorded terms. Fonts are OFL.

## Verification

Actual rendered cards were inspected while selecting and fitting the artwork,
then checked at full export size and 320 × 448. The first candidate was
rejected for rectangular artwork edges; the final action pose supplies a
clean silhouette. See `qa/ichigo-depth/review.md` and verification records.
All renderer and build source hashes matched the pre-swap snapshot. Card
definitions differed only in character identity and artwork fields.
