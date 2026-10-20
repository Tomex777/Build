# NIGHT / Manhwa — expanded candidate collection (21 October 2026)

**Implemented catalogue:** **82 distinct collectible edition candidates**, expanded from 23, across three manhwa worlds. This is **not** a claim that 82 illustrated cards are finished. Every candidate remains pending chapter-level adaptation confirmation and original artwork verification. Original artwork has not been acquired or visually approved; the lock is deliberately empty. The live NIGHT website and WhatsApp bot have **not** been changed.

## Card families and series

| Series | Power | Story spotlight | Romance duo | Total |
| --- | ---: | ---: | ---: | ---: |
| SSS-Class Revival Hunter | 11 | 12 | 1 | 24 |
| The Infinite Mage | 10 | 23 | 1 | 34 |
| Study Group | 0 | 24 | 0 | 24 |
| **Total** | **21** | **59** | **2** | **82** |

**Power** means a specific form in its own manhwa continuity; candidate ranks remain **null** until a reference-located feat audit. A character's official Tower guild standing or magical school rank is *not* a NIGHT editorial F–EX grade. A manhwa's late-novel exploits must never be retroactively pinned to a school-age webcomic portrait.

**Story spotlight** covers significant characters without giving everyone an invented combat ranking. In particular, *Study Group* has a grounded fighting/school narrative and receives no fantasy F–EX ladder. Scenes of Gamin's protection of his friends, Hankyeong's mentorship, and study-group friendships are appropriate source-specific spotlight designs.

**Romance duo** is a separate family, with two individually identified subjects and an actual relationship theme rather than power grading. Initial story-spoiler concepts are Gong-ja × Raviel (*SSS-Class Revival Hunter*) and Sirone × Amy (*Infinite Mage*). Each must use authentic **two-subject art** from the correct story continuity; neither can be fabricated by relabeling a solo portrait. The shared NIGHT finishes are a starting material choice, not a new romance-exclusive frame design.

## Expanded cast and strict publication boundaries

The additional **59 editions** include 16 SSS-Class Revival Hunter candidates, 25 Infinite Mage candidates and 18 Study Group candidates. Stable IDs from the original 23 editions remain unchanged.

- **SSS-Class Revival Hunter:** Countess, Heresy Inquisitor, Paladin, Viper, Marcus Calenbury / Sword Saint, Master Alchemist, Sylvia Evanail, Ester / Demon King of Fall Rain (distinct forms), Uburka, Hamustra, Hellfire Maiden, Lefanta Aegim, Sarbast Aegim and Goddess of Protection. Multiple versions of Sylvia require distinct poses and provenance.
- **The Infinite Mage:** Adrias Miro, Arin, Bishop Ogent, Cerielle Portris, Cuan Parka, Fermi Ardino, Guy Ogent, Isis Karmis, Jake Ardius, Kanis, Kanya, Klump Ogent, Lucas, Mark Slider, Marsha Clay, Olina Arian, Rai Ogent, Reina Ogent, Richard Pyger, Shakora Karmis, Sienna Olypher, Tess Elzaine, Thad, Victor Arcane and Vincent Arian.
- **Study Group:** Geonyeob Park, Hyunwoo Lee, Jun Lee, Minhwan Ma, Siwan Cheon, Sunchul Kim, Minho Kim, Sangin Bang, Jaehwang Chu, Taehwan Ahn / Anti, Dohoon Jung, Junghwa Oh, Yeonbaek Phi, Hansol Phi, Wooyeol Park, Jaehee Hong, Junmin Yoon and Seokdae Um.

These are **candidate identities, not 82 independently chapter-verified appearances**. Community character indexes may include figures or forms whose only substantiated appearance is in the source novel or a different adaptation. The roster records `research.verification = candidate-not-confirmed` and `webcomicAppearance = requires-chapter-check` for *all* 82 until the adapted episode is reviewed. A chapter-verified claim must include its episode number, publisher episode URL, reviewer, and review date; a publisher's general series page alone cannot pass for episode proof.

Story cards must never imply the protagonist's in-universe power rankings exist where they do not. In Study Group, even skilled martial artists are **not** assigned the fantasy power scale. Story and relationship editions may depict accurate school rivalries, bonds, turning points, or mentorship, after verifying those scenes.

### Source-identity rules

- A *character* is not the same as a *form*: Ester's saintess image cannot label her Demon King body; Sylvia's academy dress cannot substitute a later chamberlain appearance.
- Long-running stories are **continuity-specific**. For Infinite Mage and SSS-Class Revival Hunter, do not assign later novel feats to webcomic-era character images.
- Naming aliases (e.g. Sirone/Shirone, Viper/Venomous Snake, Gamin Yoon/Yoon Ga-min) are search aids, **not additional card editions**.
- The two initial romance duos must remain two-subject art editions rather than a single protagonist portrait with a romantic caption.

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
- **Study Group:** [official WEBTOON](https://www.webtoons.com/en/action/study-group/list?title_no=3595), [YLAB publisher entry](https://welcon.kocca.kr/en/directory/content/study-group--8938), [member list (community)](https://webtoon.fandom.com/id/wiki/Study_Group).

Published translations vary (Gong-ja / Confucius Kim, Sirone / Shirone, Gamin Yoon / Youn Ga-min). Catalogue IDs are stable, and editorial display names follow the webcomic-first convention. Wikipedia, Fandom, and community articles are **discovery evidence**, not sufficient to verify an SSS/EX feat or issue a commercial image license.

## Research gallery (no fake art)

Build the standalone, spoiler-aware research browser with:

```sh
cd cards
npm ci
node bin/manhwa.mjs audit
node bin/manhwa-review.mjs
# Open dist/manhwa-research/index.html
```

The page filters all 82 records by series, family, and translated aliases; plot-spoiler cards are hidden by default. **It is a list of candidate identities, not CSS copies of the actual collectible-card artwork.** The dedicated CI workflow uploads its HTML and machine-readable summary as `NIGHT-Manhwa-82-Candidate-Roster`. The preview is not the main NIGHT website and needs no art distribution permissions.

## Steps to actual cards

1. Find legitimate rights-cleared/permissioned **manhwa** character cutouts for the depicted forms. Do not silently substitute an anime adaptation or novel-era image. Record the original source and its permission.
2. For a solo edition, place the *unchanged* official transparent original under `cards/artwork/manhwa/<series>/<id>.png` and add its verified native dimensions, SHA-256, exact artwork URL, rights, crop, focal point and subject/form identity to `cards/catalog/manhwa-art-lock.json`. This folder is ignored by Git; preserve source files separately and never commit without rights.
3. Run `cd cards && npm ci && node bin/manhwa.mjs audit && node bin/manhwa.mjs prepare`. This preflights exact bytes, dimensions and alpha, and emits `catalog/<series>.json` **only for series with verified art**. Original source changes invalidate its lock; recheck and repin explicitly.
4. Render an acquired solo set with `npm run build -- --series sss-revival --width 3000 --out dist/sss-revival` (or the other series), then check depth and all phone proofs and seek actual visual approval.
5. Two-subject romance requires a proper provenance-preserving two-layer composition feature; the preparer deliberately refuses to flatten/pretend a duo is ready.
6. Never publish to the live NIGHT site or bot until art is licensed, physically reviewed and a separate integration/release is approved.

No fabricated artwork, unearned ranks, mystery source licenses, visual-approval flags or production deployment exist in this foundation.
