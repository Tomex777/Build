# NIGHT / Manhwa — first candidate collection (21 October 2026)

**Implemented foundation:** 23 distinct collectible *edition specifications* across three manhwa worlds. This is not a claim that 23 illustrated cards are finished. Original artwork has not been acquired or visually approved; the lock is deliberately empty. The live NIGHT website and WhatsApp bot have **not** been changed.

## Card families and series

| Series | Power | Story spotlight | Romance duo | Total |
| --- | ---: | ---: | ---: | ---: |
| SSS-Class Revival Hunter | 6 | 1 | 1 | 8 |
| The Infinite Mage | 5 | 3 | 1 | 9 |
| Study Group | 0 | 6 | 0 | 6 |
| **Total** | **11** | **10** | **2** | **23** |

**Power** means a specific form in its own manhwa continuity; candidate ranks remain **null** until a reference-located feat audit. A character's official Tower guild standing or magical school rank is *not* a NIGHT editorial F–EX grade. A manhwa's late-novel exploits must never be retroactively pinned to a school-age webcomic portrait.

**Story spotlight** covers significant characters without giving everyone an invented combat ranking. In particular, *Study Group* has a grounded fighting/school narrative and receives no fantasy F–EX ladder. Scenes of Gamin's protection of his friends, Hankyeong's mentorship, and study-group friendships are appropriate source-specific spotlight designs.

**Romance duo** is a separate family, with two individually identified subjects and an actual relationship theme rather than power grading. Initial story-spoiler concepts are Gong-ja × Raviel (*SSS-Class Revival Hunter*) and Sirone × Amy (*Infinite Mage*). Each must use authentic **two-subject art** from the correct story continuity; neither can be fabricated by relabeling a solo portrait. The shared NIGHT finishes are a starting material choice, not a new romance-exclusive frame design.

## Existing NIGHT visual contract

- Reuse the actual code in `cards/src/render.mjs` and `cards/renderer`, not CSS fake cards, AI-generated images, or a new franchise-specific layout.
- Subject hair, arms or clothing must cross the **inner frame**. Depth remains mandatory for power, story and romance editions.
- Genuine 3000 × 4800 SVG/PNG exports plus 320 × 512 phone proofs; inspect every full-size and phone image before visual approval.
- Preserve Obsidian, Nocturne and all old BLEACH renders. No card is approved by CI.
- Maintain the correct form and its exact source bytes, SHA-256, native pixel size, rights statement, owner credit, crop and focal location.
- Official promo art is **copyrighted**; publisher availability does not grant rights to sell, redistribute or publish it. These proposals are research/reference-only. Obtain distribution rights before any public commercial rollout.

## Sources

- **SSS-Class Revival Hunter:** [official Tapas publication](https://tapas.io/series/sss-class-revival-hunter/info), [Yen Press](https://yenpress.com/series/sss-class-revival-hunter), [character identity reference (community, spoiler-heavy)](https://sss-class-revival-hunter.fandom.com/wiki/Category:Character).
- **The Infinite Mage:** [official Tapas webcomic](https://tapas.io/series/the-infinite-mage/info), [official Tapas novel](https://tapas.io/series/the-infinite-mage-novel/info) *only for separating adaptations, never to inflate webcomic tiers*, [cast index (community)](https://infinite-mage.fandom.com/wiki/Category:Characters).
- **Study Group:** [official WEBTOON](https://www.webtoons.com/en/action/study-group/list?title_no=3595), [YLAB publisher entry](https://welcon.kocca.kr/en/directory/content/study-group--8938), [member list (community)](https://study-group.fandom.com/wiki/Characters).

Published translations vary (Gong-ja / Confucius Kim, Sirone / Shirone, Gamin Yoon / Youn Ga-min). Catalogue IDs are stable, and editorial display names follow the webcomic-first convention. Wikipedia, Fandom, and community articles are **discovery evidence**, not sufficient to verify an SSS/EX feat or issue a commercial image license.

## Steps to actual cards

1. Find legitimate rights-cleared/permissioned **manhwa** character cutouts for the depicted forms. Do not silently substitute an anime adaptation or novel-era image. Record the original source and its permission.
2. For a solo edition, place the *unchanged* official transparent original under `cards/artwork/manhwa/<series>/<id>.png` and add its verified native dimensions, SHA-256, exact artwork URL, rights, crop, focal point and subject/form identity to `cards/catalog/manhwa-art-lock.json`. This folder is ignored by Git; preserve source files separately and never commit without rights.
3. Run `cd cards && npm ci && node bin/manhwa.mjs audit && node bin/manhwa.mjs prepare`. This preflights exact bytes, dimensions and alpha, and emits `catalog/<series>.json` **only for series with verified art**. Original source changes invalidate its lock; recheck and repin explicitly.
4. Render an acquired solo set with `npm run build -- --series sss-revival --width 3000 --out dist/sss-revival` (or the other series), then check depth and all phone proofs and seek actual visual approval.
5. Two-subject romance requires a proper provenance-preserving two-layer composition feature; the preparer deliberately refuses to flatten/pretend a duo is ready.
6. Never publish to the live NIGHT site or bot until art is licensed, physically reviewed and a separate integration/release is approved.

No fabricated artwork, unearned ranks, mystery source licenses, visual-approval flags or production deployment exist in this foundation.
