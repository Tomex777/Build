# NIGHT Naruto collectible cards — independent preview

**Status:** 29 sourced portrait candidates, 17 unsourced transformations. No visual approvals, no production site changes.

This Naruto expansion reuses the existing NIGHT/BLEACH/Re:Zero shared render library at `cards/src/`; it creates **no new Naruto-only frames**. Its official character portraits are real transparent PNG cutouts from Bandai Namco's Naruto Shippuden Ultimate Ninja STORM 4 character pages. Only the actual figure — not a glowing aura — may cross the inner frame. Each source carries its own exact matching form/page identity.

## Candidate pipeline

```sh
cd cards
npm ci
node bin/seed-naruto.mjs --write-lock
npm test
# Install pinned Chrome 131 from cards/README.md first
npm run build -- --series naruto --width 3000 --out dist/naruto
npm run gallery -- --series naruto --out dist/naruto --file dist/Naruto-Collection.html
node bin/naruto-gallery.mjs dist/naruto/index.html
node bin/naruto-gallery.mjs dist/Naruto-Collection.html
```

`.github/workflows/cards-naruto.yml` runs the acquisition, checks the pinned native source SHA-256/dimensions, renders true 3000×4800 PNG masters and 320px proofs, and exports only low-resolution **candidate** gallery assets to `cards/naruto-preview/` on the feature branch. It never merges to the `cards` base, main NIGHT site, or bot. It does not publish a card if an source is missing or invalid; those failures must be corrected, not hidden with mock cards or mislabeled stock art.

No preexisting edit is considered visually approved: human review must inspect native source art, the whole master at 3000×4800, native-pixel facial edges, silhouette frame breakout and 320×512 phone proofs. Curated source files average about 512×1000; a 3000px export is not equivalent to genuinely 4K input art. Acquire higher-resolution originals when available; never imply artificial upscale creates real details.

## Editorial power

Draft grades shown in the candidates are relative Naruto continuity-only placeholders inherited from the existing NIGHT in-universe renderer. They are **not** a verified cross-verse Bleach/Solo Leveling ranking. The series should be compared across consistent shared benchmarks for strength, speed, durability, dimensional/space-time effects and counterplay before the user accepts power ranks or any SSS/EX assignment. Boruto Baryon Mode is a separate pending source and never uses Six Paths artwork.

## Copyright and distribution

All portrait art is © its respective Naruto, Shueisha, TV Tokyo, Pierrot and Bandai Namco rightsholders; **all rights reserved**. These are reference-only private candidates, not a granted license for NFT, merchandise, collectible commerce or public redistribution. Keep the preview protected and do not roll out public production assets without obtaining rights.

## Feedback gate

The preview is a candidate gallery only. Approval, trading/drop rates, NIGHT bot integration, broad distribution and the main NIGHT website remain blocked until the user authorizes those steps separately.

## Visual identity correction from actual rendered proof

The Storm 4 official character page `08_08` depicts **masked Obito portraying himself as Madara** (Tobi's Madara persona), not the real living Madara. A full-size rendered proof exposed this mistake. The former `madara-war` card was withdrawn and renamed `obito-masked-persona`, with a unique A-tier form appraisal; the historical page name is not reliable proof of the depicted person's identity. Genuine living Madara now has a separate, *pending* unsourced entry. No old mislabeled preview export may be retained. This must be reverified against regenerated PNGs, as all other art and breakout compositions must be.
