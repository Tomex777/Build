# ONE PIECE / NIGHT universal cards

**Source: [official One Piece Film Red character profiles](https://www.onepiece-film.jp/).** No image generation, no separate One Piece card design. This collection uses exactly the same NIGHT renderer, materials, frame paths, glass power glyphs, inner-frame character breakout, 3000×4800 masters and 320×512 proofs piloted for Bleach.

## First edition — eleven verified source identities
The official character image pages provide two-outfit transparent character sheets, not individual ready-to-print cutouts. Each source is pinned with **raw SHA-256, original native dimensions, exact normalized extraction rectangle, processed SHA-256, isolated pixels and transparent padding**. The acquisition script extracts existing character pixels; it never invents anatomy, removes backgrounds with AI or substitutes another form's artwork. Raw high-quality character cuts should replace these when sourced and visually reviewed.

Characters: Luffy, Zoro, Nami, Usopp, Sanji, Chopper, Robin, Franky, Brook, Jinbe, Shanks. The listed form is the actual **FILM RED illustrated costume**, not Gear 5/King of Hell/Ifrit Jambe/Demonio Fleur. **Those transformed looks are intentionally not claimed.** More cards/poses can be added only with distinct matching art and exact form-specific rank assessments.

The rank tier determines which shared NIGHT finish to use: F/Vanguard, E/Ivory, D/Voltage, C/Crimson, B/Tidelight, A/Nocturne, S/Emberglass, SS/Obsidian, SSS/Eclipse, EX/Ascendant. Ranks are editorial, within ONE PIECE's continuity, and do not assert equality to Bleach or other verses. SSS/EX require a source-located verified feat file.

## Run locally
```sh
cd cards
npm ci
node bin/seed-one-piece.mjs --write-lock
npm test
# Install the locked Chrome 131 binary per the top-level cards README.
npm run build -- --series one-piece --width 3000 --out dist/one-piece
npm run gallery -- --series one-piece --out dist/one-piece --file dist/NIGHT-One-Piece-Collection.html
```

The branch CI workflow `.github/workflows/cards-one-piece.yml` performs those steps, pins sources in Git and uploads candidate masters, phone proofs, contact sheets and an offline searchable gallery as an artifact. A failed fetch, incorrect art SHA, insufficient source content, missing silhouette-breakout validation, bad glyph or misfit character is an error; do not downgrade it to a success. Existing unrelated published collections are not touched.

**Mandatory human visual review before approval or publication:** inspect each 3000×4800 export, zoom the character borders and typography at native pixels, inspect the 320px mobile proof, and reject any mixed-figure costume sheet or accidental clipping. If it contains multiple figures or insufficient native detail, acquire alternative licensed/publicly accessible cutout art for that specific form; do not hide or swap it under another character label.

Rights reserved © original ONE PIECE authors and production holders. Official availability does not grant distribution/commercial merchandising rights. The source provenance and editorial ranks are research records, not licenses. This is a candidate collection only, not an approved bot asset or public production release.
