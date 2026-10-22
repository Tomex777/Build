# NIGHT Cards — Demon Slayer / Mitsuri Kanroji

**Status: official original-art candidate in progress; NOT approved or deployed.** The intended card is a real renderer-produced SVG/PNG, never an AI-generated image, isolated HTML mockup or independent Mitsuri-specific design.

- **Character:** Mitsuri Kanroji, Love Hashira, Swordsmith Village.
- **Official source:** https://game.kimetsu.com/hinokami2/character/?chara=32
- **Exact original illustration:** https://game.kimetsu.com/hinokami2/assets/img/character/main/chara32.png
- **Form-specific in-universe proposal:** `A` / standard NIGHT **Nocturne** finish, subject to later editorial review. Unmarked Mitsuri has extraordinary muscle density and Love Breathing swordsmanship, and initially counters Zohakuten's wooden dragons but is vulnerable to his compressed sound attack. Later sustained fighting after awakening the Demon Slayer Mark is NOT evidence for the unmarked edition. Her marked state needs matching original artwork and a separate review. Official episode synopsis: https://demonslayer-anime.com/sva/story/10.html . Being a Hashira does not itself prove S/SS/SSS/EX.
- **Visual invariant:** subject pixels, not aura, must physically cross the original inner frame. Preserve shared NIGHT Nocturne art direction, layered depth, glass rank glyph, unchanged typography/frame geometry, 3000x4800 masters, and 320x512 proofs. Don't reintroduce circular backdrop or obsolete emblem.
- **Source safety:** exact artwork SHA-256, native dimensions, alpha and actual character identity must be reviewed. No AI image generation or AI upscaling.
- **Rights:** official art is rights-reserved, usable only as a reference candidate pending permission/clearance before public/commercial publication.

## Run with repository pipeline

```bash
cd cards
npm ci
node bin/seed-demon-slayer.mjs --write-lock
npm test
# Install pinned browser from cards/README.md.
npm run build -- --series demon-slayer --width 3000 --out dist/demon-slayer
npm run gallery -- --series demon-slayer --out dist/demon-slayer --file dist/DemonSlayer-Collection.html
node bin/demon-slayer-gallery.mjs dist/DemonSlayer-Collection.html
```

The official-art ingestion fails closed on bad pixels, wrong formats, missing alpha, changed bytes, missing official URLs, or unverified dimensions. Candidate exports and phone proofs are uploaded by the GitHub workflow, **not auto-approved or publicly deployed**. A numeric breakout pass is necessary but a person must inspect hair/hand/sword silhouette across frame and native-scale details before creating an approval record.
