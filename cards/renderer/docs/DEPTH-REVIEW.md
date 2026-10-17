# Depth Editions — visual review

Reviewed from actual Chromium renders, 16 October 2026. No generated card
artwork, AI illustration or AI upscaling was used.

## Reference principles

The existing research covers Pokémon illustration/full-art cards, Topps
Chrome/Sapphire and premium anime collectibles. This pass additionally
inspected the official Pokémon Charizard ex 223 card image and reviewed
Topps' card-technology glossary. These informed full-art hierarchy, optical
engraving and selective reflection. The frame, hallmark, typography and
surface geometry here are original; no game statistics were added.

- https://www.pokemon.com/us/pokemon-tcg/pokemon-cards/series/sv03/223/
- https://www.topps.com/pages/card-tech-glossary
- Previous research: `RESEARCH.md`.

## Iterations and decisions

| Render | Observed weakness | Revision |
| --- | --- | --- |
| Depth 01, 1500 × 2100 | The two finishes shared too much circular geometry. The new border and character shadow gave depth, but the pale finish lacked its own identity. | Kept the dark engraved disc for Nocturne; gave Tidelight an asymmetric pearl aperture and flowing fresnel curves. |
| Depth 02, 1500 × 2100 | The lower rosette resembled a mechanical gear; the edge light was heavier than necessary. | Replaced it with a small faceted star hallmark and reduced the character rim. |
| Depth 03 and final masters | A moving highlight also needed a corresponding shadow and consistent exported layer positions. | Unified the light coordinates driving shadows, material gradients and three depth planes; checked live/export agreement. |

The final composition leaves the illustration's face clean and uses its red
scroll and diagonal pose as the main movement. The character crosses the
inner metal aperture; its alpha channel creates the edge lighting and cast
shadow. The name sits below the face and remains readable at small sizes.
Nocturne uses dark chrome and radial engraving. Tidelight uses softer pearl
reflections and a visible recessed background. Fine diamonds, corner lines,
bezel highlights and a small hallmark reward closer inspection without
turning into extra information panels.

## Final image inspection

- Full cards were inspected from the completed 6000 × 8400 PNG masters.
- Native-resolution crops checked corner engraving, lettering and tile joins.
  The frame geometry stays crisp. The illustration visibly softens when
  enlarged because its original image is 1576 × 1884.
- Both 12000 × 16800 JPEGs were decoded completely, with native detail crops
  and reduced overview proofs checked separately.
- 320 × 448 card proofs retain the face, pose, name and edition. Tiny credit
  text and engraving are deliberately close-inspection detail, not essential
  small-screen content.
- The viewer was checked at a 390 px viewport. These are small-screen viewing
  proofs; no claim is made to have sent the files through WhatsApp or tested
  WhatsApp's current compression pipeline.
- The motion recording contains 60 actual rendered light/depth states from
  the same SVG renderer, not a fabricated foil animation applied to a still.

## Engineering checks

- 16 automated tests passed, including deterministic depth rendering,
  identity escaping, input validation and the existing character/discovery
  tests.
- Browser verification covered both finishes, reverse, foil control, keyboard
  light, reduced motion, 390 px layout, offline use and 3000 × 4200 export.
  There were no page errors or external network requests.
- Live and exported material gradients, depth transforms and shadow offsets
  agreed at two distinct light positions for both finishes.
- A direct-versus-tiled 1000 × 1400 comparison had mean absolute channel error
  0.064 on a 0–255 scale, with small antialiasing differences. The export is
  not claimed to be bit-identical to one giant browser screenshot.
- An incomplete early large PNG was caught by full decoding. The large-image
  writer now completes encoding in a temporary directory, verifies it,
  synchronizes the copied file and publishes it with an atomic rename.
  All four delivered large images passed full decode checks.
- The preserved archive passed ZIP CRC validation and its recorded SHA-256
  matched. Every original working source file matched its saved hash.

## Limits

8K and 16K describe the rendered card canvas. Vector decoration and type are
rendered at that density; the original illustration gains no invented detail.
An authorized, higher-resolution cutout can replace it using the same character
profile workflow. The Naruto example remains copyrighted artwork, separate
from the MIT-licensed rendering code. Digital foil does not supply physical
printing, embossing or stamping production plates.
