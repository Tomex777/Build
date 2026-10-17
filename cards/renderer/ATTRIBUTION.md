# Artwork and fonts

## Naruto — Depth Editions 2.0

The Naruto example uses the official illustration by **Masashi Kishimoto**,
**© SHUEISHA**, from https://naruto-official.com/en/about . Its native image is
1576 × 1884. The exact source URL, hash and rights statement are recorded in
`examples/characters/naruto.json`. It was positioned and combined with the
original NIGHT frame and lighting effects. No AI generation or upscaling was
used. Naruto artwork rights are reserved; the Kiki CC BY-SA terms below do
**not** apply to Naruto images, renders, previews or motion demonstrations.
The software's MIT license does not license this illustration for sale.

## Kiki — original example collection

The three showcase illustrations are by **Tyson Tan**, depicting **Kiki**, the
Krita mascot. Krita's official mascot gallery explicitly licenses these works
under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) as an
alternative to GNU GPL. This package uses the CC BY-SA option.

Official source and license declaration: https://krita.org/en/about/mascot/

| Card | Original artwork | Original pixels | Included pixels |
| --- | --- | --- | --- |
| Nocturne | Kiki's Unplanned Canvas Expansion (Fullbody) | 4056 × 5271 | 2400 × 3119 |
| Tidelight | Kiki Paints Over the Waves | 10000 × 5000 | 6000 × 3000 |
| Verdant | Kiki with a Parasol | 10000 × 5000 | 6000 × 3000 |

Direct original URLs, download dates, and original/normalized SHA-256 hashes
are retained in `assets/catalog.json`. License evidence is recorded in
`docs/artwork-evidence.json`.

Changes: illustrations were resized and converted to WebP, cropped or
positioned, and combined with original NIGHT SVG framing, type, and procedural
material effects. **Adapted card images, their SVGs, overview screenshots, and
foil demonstration are distributed under CC BY-SA 4.0.** Retain the artist's
credit, source link, license link, and change notice. The accompanying
`dist/*-caption.txt` files provide attribution for image sharing.

Nocturne uses the artist-provided transparent character image; the renderer did
not generate, redraw, or trace the character. No AI image generation was used.

## Fonts

- Display and true italic: Cormorant Garamond, Cormorant Project Authors, SIL Open Font License 1.1.
  Source: https://github.com/google/fonts/tree/main/ofl/cormorantgaramond
- Sans: DM Sans, DM Sans Project Authors, SIL Open Font License 1.1.
  Source: https://github.com/google/fonts/tree/main/ofl/dmsans

The v1.1 update adds an original SVG botanical frame, orbital backplate, pearl field, and a true italic title. The licensed illustrations were not regenerated or redrawn.

Bundled fonts are static 500-weight subsets, Latin and Latin Extended A plus
common punctuation; DM Sans uses optical size 24. Full OFL notices are in
`assets/licenses/`. Supply additional appropriately licensed font coverage for
other scripts.

## Software and reference products

Original source code is MIT licensed. Third-party npm dependencies retain their
own licenses. Pokémon, Topps, and Weiß Schwarz references were studied for
composition and material behavior; none of their logos, card frames, signatures,
or artwork is included in the renderer or showcase.
