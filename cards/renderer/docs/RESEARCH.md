# Collectible design study

Reviewed 16 October 2026. Reference artwork is not redistributed here.

| Reference | Source | Principle used in NIGHT |
| --- | --- | --- |
| Pokémon illustration rares | [Pokémon: Paldea Evolved illustration rare cards](https://www.pokemon.com/us/pokemon-news/a-look-at-pokemon-tcg-scarlet-violet-paldea-evolved-illustration-rare-cards) | Character and setting carry the card. Favor a generous picture field and a single, clear identity. |
| Pokémon full-art foil | [Pokémon: card aesthetic](https://www.pokemon.com/us/pokemon-news/pokemon-tcg-scarlet-and-violet-revamps-pokemon-tcg-card-aesthetic) | Separate the illustration's color from the reflective finish; use the finish to support the image. |
| Topps Chrome Sapphire / Superfractor | [Topps official F1 Sapphire gallery](https://www.topps.com/pages/formula-1-chrome-sapphire) | Specular edges, directional metal, repeating engraved detail, and a restrained collectible number. The official Sapphire and Superfractor images were inspected. |
| Weiß Schwarz premium anime cards | [Bushiroad product announcement](https://en.ws-tcg.com/products/bp-ks/), [official SP variation announcement](https://en.ws-tcg.com/information/modsp-shs3/) | Hot-stamped names and selective metallic accents establish a second hierarchy over the art. Avoid imitating signatures or falsely implying an autograph. |

The NIGHT identity uses a double machined rim, stepped corner geometry,
a four-point light emblem, currency-like guilloché, contrasting serif and
tracked sans typography, and deep ink fields. The three finishes share this
identity without replacing their artwork's original palette.

The foil is purposefully selective. A wavy SVG mask keeps the central face
outside the surface sheen; fine spectral contours run through the lower
picture, while the border and engraved seal catch stronger light. The grain,
curves, hatch, gradients, seal, text, and frame are all code. Only the licensed
illustration and font outlines are external assets.

There are no combat statistics, health bars, ability text, randomized rarity
claims, fabricated autographs, or scarcity claims. 001–003 identify the three
compositions, not a print run or ownership certificate.

## Artwork discovery documentation

- [Official Krita mascot gallery and license](https://krita.org/en/about/mascot/)
- [MediaWiki Imageinfo API](https://www.mediawiki.org/wiki/API:Imageinfo)
- [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)

Live checks found 12 metadata-eligible Krita gallery candidates. A selected
transparent illustration was reacquired and decoded at 4056 × 5271; its SHA-256
matches the original used for Nocturne. A separate Commons Hokusai query
returned two candidates after the license and resolution filters. These
results are archived in `qa/discovery-*-live.json`.

Discovery returns candidates, not visual approval. Every newly acquired or
registered asset stays marked `visualApproval: false` until its actual card
composition has been rendered and inspected. Rights metadata is rechecked
before every acquisition, and unknown/NC/ND/custom licenses are rejected.
