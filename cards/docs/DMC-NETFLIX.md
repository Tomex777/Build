# NIGHT / Devil May Cry — Netflix (Adi Shankar, Studio Mir)

**Only the Netflix animated series, seasons 1 (2025) and 2 (2026)**. This collection is *not* the Capcom game canon, 2007 Madhouse anime or DmC reboot. Source of main facts: [Netflix Tudum](https://www.netflix.com/tudum/articles/devil-may-cry-release-date-photos), [series episodes/cast](https://en.wikipedia.org/wiki/Devil_May_Cry_(TV_series)), [S2 finale recap](https://www.whats-on-netflix.com/news/devil-may-cry-season-2-ending-explained/).

The 36-edition roster in `catalog/dmc-netflix-roster.json` is **power research**, not a claim of 36 finished cards. Ranks are provisional NIGHT editorial *in-verse* comparisons. Forms are not interchanged, and any unsupported Netflix animation feats from the videogames are forbidden. The S2 highest bracket is proposed as **Mundus EX**, **Argosax (revived) SSS**, **Sparda (historical) SSS**; an unresolved Vergil–Mundus fight never proves Vergil defeated Mundus.

The artwork-backed pilot is restricted to Dante (base) and Mary Arkham's season-one DARKCOM uniform; references are source-bound and all rights remain Netflix/Capcom/Studio Mir. Their public wiki mirrors do *not* confer redistribution permissions. The seed may reject bad images; it must not silently use DMC game renders, rename faces, or pass an opaque square as a foreground silhouette.

```sh
cd cards
npm ci
node bin/seed-dmc-netflix.mjs
npm test
npm run build -- --series dmc-netflix --width 3000 --out dist/dmc-netflix
npm run gallery -- --series dmc-netflix --out dist/dmc-netflix --file dist/NIGHT-DMC-Netflix.html
node bin/dmc-netflix-gallery.mjs dist/dmc-netflix/index.html
node bin/dmc-netflix-gallery.mjs dist/NIGHT-DMC-Netflix.html
```

The real shared NIGHT renderer creates 3000×4800 PNG, SVG, 320×512 phone proof and gallery from legitimate alpha cutouts. White-background separation is a *candidate* only, requiring manual contour inspection. **Never approve automatically**. Artwork pins, forms, native dimensions and original SHA are preserved and renderer QA is not a substitute for human visual acceptance. No bot publication, main-site merge or commercial release is authorized from this branch.

Pending editions remain in the roster until individually matched animated-series visuals are available. Do not represent a source roster, preview list, upscaled pixels or CSS mock as completed collector cards.
