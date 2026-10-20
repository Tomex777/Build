# NIGHT Cards — Bungo Stray Dogs collection

## Status
- **Universe:** Bungo Stray Dogs (文豪ストレイドッグス), the manga by Kafka Asagiri and Sango Harukawa, with a Bones anime adaptation. This import uses **official anime character cutouts**, not unsourced or generated pictures.
- **Roster:** 34 standard cast records, including Armed Detective Agency, Port Mafia, The Guild, Decay of the Angel, Hunting Dogs, Dark Era and Dead Apple continuity. Each has a separately proposed power band and qualifying limitations.
- **Source map:** 19 named official full-body character portrait URLs, each bound to the correct named official profile. Fetching and checksum pinning happens in the acquisition step. The remaining 15 records are **not** completed cards.
- **Major additional forms:** Atsushi tiger, Chūya Corruption, Dazai Dark Era and vampiric Akutagawa are separately *pending*, not mislabeled costumes.
- **Publication:** candidate renders only. **No artistic approvals and no release to the production website, NIGHT bot or a monetized game.**
- **Rights:** © original creators / KADOKAWA / Bungo Stray Dogs anime production. Official publication is not a public-use licence. Original files and exports must not be publicly distributed until rights are cleared.

## Shared design and rankings

BLEACH was the reference gallery, **not an exclusive holder of NIGHT's card design system**. This collection uses the same ten NIGHT renderer design families, typography, glass rank glyphs and intentional real-silhouette frame breakout (never aura-only). No franchise-exclusive card geometry or unrelated CSS simulation. Proposed form-specific, **in-universe** NIGHT grades F → E → D → C → B → A → S → SS → SSS → EX, applied through the shared tier-to-finish mapping. Nothing equates a BSD A with a BLEACH A. Lower ranks reflect *direct combat strength*, while strategically exceptional characters such as Ranpo and Dazai retain separate information or nullification utility in assessments. Special gear and alternate states require separate records. SSS and EX must be supported by verified form-specific feat records, not enthusiasm or popularity.

## Running candidate acquisition and rendering

```sh
cd cards
npm ci
node bin/seed-bungo.mjs --write-lock
npm test
# When there are at least one source-backed entries, install pinned Chrome per cards/README.md
npm run build -- --series bungo-stray-dogs --width 3000 --out dist/bungo-stray-dogs
npm run gallery -- --series bungo-stray-dogs --out dist/bungo-stray-dogs --file dist/Bungo-Stray-Dogs-Collection.html
node bin/bungo-gallery.mjs dist/Bungo-Stray-Dogs-Collection.html
```

The feature-branch CI uses the same shared renderer, produces actual 3000×4800 PNG and 320×512 phone proofs for source-backed characters only, checks subject/frame contact programmatically, and uploads a candidate gallery for human inspection. QA is not artistic approval: review the **actual master image**, hair/hand outlines at native resolution, inner-frame breakout of the *character*, and phone proof before approving. Each source URL, source SHA, width/height, crop and rank is fingerprinted; a replacement invalidates the review. Captured source rights are reference-only.

Official reference: https://bungo-stray-dogs.jp/character/ (character portraits are rights reserved). Existing NIGHT cards guidelines: `cards/AGENTS.md`, `cards/README.md`. This branch stacks on the Re:Zero candidate branch to preserve the ten-family shared renderer additions. **Neither branch implies a completed live publication.**
