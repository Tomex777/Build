# NIGHT Play Jigsaw — photographic gallery

New puzzles use **real photographs**, not generated illustrations or procedural placeholders.

## Source and license
- The 14 photographs are individually selected from Unsplash and loaded from `images.unsplash.com`.
- License: https://unsplash.com/license (Unsplash License; photography remains independently licensed from the MIT jigsaw engine).
- The exact image asset IDs, titles, categories, and required 4:3 crop are maintained in `photo-gallery.js`. That file is the authoritative photo inventory.
- The image CDN serves responsive 480×360 gallery thumbnails and 1200×900 playing images. A selected photograph is painted into the jigsaw engine's 800×600 canvas; all pieces and the reference use this *same crop*.
- Unsplash's license allows commercial or non-commercial use, while prohibiting replication of an image library. This curated set exists only for playing jigsaws.

## Behavior
- Curated photos **require connectivity** when first fetched (and when resuming after cache eviction). Do not advertise them as offline-first.
- Failure to fetch a picture should leave the gallery usable and avoid destroying an unfinished puzzle.
- The original seven procedurally drawn puzzle images are still renderable only for existing in-progress saves. They are no longer selectable in the gallery.
- Players' custom uploads remain local to their browser and are not sent to Unsplash or NIGHT servers.
- Please visually QA real CDN images on mobile and desktop before production deployment. Automated tests mock remote images for deterministic gameplay testing; mocks are not a substitute for this QA.

Photo provider: https://unsplash.com
