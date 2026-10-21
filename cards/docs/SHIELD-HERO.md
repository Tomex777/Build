# NIGHT / The Rising of the Shield Hero

**Collection status:** 16 source-pinned candidate editions (initial anime batch). These are NOT automatically approved, licensed, published or playable drops. Do not merge into the public NIGHT card store/bot until source rights, image QA and user approval are complete.

## Rendering contract

This collection uses NIGHT's **exact shared BLEACH/Re:Zero/Naruto renderer** and existing ten tier materials. Do not add Shield-Hero-only HTML mock cards, badges, borders, CSS templates or AI illustrations. The genuine PNG figure must visibly **break through the inner card frame**; no effects-only breakout, no falsely renamed images, no duplicated character silhouette across different forms.

Every successful build generates a 3000 × 4800 PNG master, renderer SVG, 320 × 512 proof, source and grade metadata, SHA-256 checksums, contact sheets and an offline search/filter/gallery. Build failures are quarantined, not replaced with placeholders or another character's illustration.

## Source roster

`catalog/shieldhero-sources.json` contains 16 *distinct* official anime transparent figure illustrations: Naofumi, Raphtalia, Filo, Rishia, Ren, Motoyasu, Itsuki, Melty, Eclair, Myne, Sadeena, Fohl, Atla, S'yne, Werner and Jaralis.

Art is the specific character's Season 3/4 official pose, sourced from:
- https://shieldhero-anime.jp/en/3rd/
- https://shieldhero-anime.jp/en/4th/

These official pages establish the identities and several abilities or conditions used for the **in-universe NIGHT power grade proposals**. The ranking isn't official and does not equate a shieldhero S with an S from another anime.

The initial visible ranks range **D–S**. This is a deliberate evidence boundary, *not* a claim that the franchise has no SS/SSS/EX-level beings: Fitoria, Spirit Tortoise, Phoenix, Glass, Kizuna, L'Arc, Therese, Takt, the Demon Dragon, late light-novel forms and web-novel-only divine forms remain **pending matching, source-vetted artworks and continuity-specific feat review**. Especially, Naofumi's regular anime Shield Hero image must never be silently labeled as Zero Series, Wrath III or web-novel deity. Sadeena's ordinary harpoon/arena image isn't labeled her later Vassal Harpoon state.

## Rights

All official illustration sources are *rights reserved*. The Season 4 reuse notice is limited to its specified platforms/uses, and does **not** grant generic merchandising rights or a blanket right to publish a standalone collectible website. Local candidate builds and private review artifacts do not constitute distribution permission. Hold public gallery and bot publishing pending clearance.

## Reproduce the real cards

```sh
cd cards
npm ci
node bin/seed-shieldhero.mjs --write-lock
npm test
# Install pinned Chrome 131.0.6778.204 per cards/README.md
npm run build -- --series shieldhero --width 3000 --out dist/shieldhero
npm run gallery -- --series shieldhero --out dist/shieldhero --file dist/ShieldHero-Collection.html
node bin/shieldhero-gallery.mjs dist/ShieldHero-Collection.html
```

The source lock is intentionally empty in the initial commit. First successful CI acquires the official PNG bytes, validates native dimensions and alpha, writes the exact file SHA-256 and derived form-specific catalogues, and commits those provenance manifests on the feature branch. Subsequent runs fail closed if source art changes rather than silently swapping it.

The workflow `.github/workflows/cards-shieldhero.yml` uploads `NIGHT-ShieldHero-Candidate-Cards`, including complete masters and the actual renderer gallery. **The artwork is never declared aesthetically approved by CI.** A reviewer still must inspect full size, native-pixel hair/hand contours, inner-frame foreground/body silhouette breakout, and small 320px proofs. QA or licensing omissions block publication.

## Outstanding work

- Source and match artwork for major missing fighters and each actual transformation. Do not reuse an ordinary-form portrait and pretend it's an awakened form.
- Check candidate visual balance and figure depth **individually**; adjust geometry and source-bound masks where necessary.
- Verify late-light-novel and *separate web-novel* tiers against source-localized feats; require `featReview` records for SSS/EX.
- Obtain appropriate publication rights and request user approval before merging into main NIGHT Cards.
