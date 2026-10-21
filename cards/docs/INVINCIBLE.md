# NIGHT Invincible — source-bound collectible card candidates

**Scope:** 21 official fighter full-body portraits acquired from Skybound's *Invincible VS* (21 actual renders if CI passes); 20 separate **unrendered** editions/forms in the research backlog. All use the existing universal NIGHT frame family, at 3000 × 4800, with head/arms/legs overlapping the inner frame. No static HTML imitation or synthesized character artwork.

**Continuity split:** Ratings describe the named character's usual core *Invincible* comic combat profile, **not** competitive fighting-game moves or a claim that the game's artwork depicts a specific comic story beat. Ella Mental is original to *Invincible VS* and her grading is only provisional in that game's continuity. No extra prestige should be inferred from the artistic finish. SSS and EX are not interchangeable with how rare a card is.

### Top-tier power decisions

- **SSS:** Battle Beast's shown armed baseline: direct prolonged mortal combat with Thragg (Invincible #115–120); Thragg ultimately wins. Grand Regent Thragg and late/emperor Mark are proposed **SSS editions pending a form-accurate original image**. No fake EX can be minted by merely recoloring Mark.
- **SS:** Omni-Man, Conquest, and strong/post-recovery Allen. Distinct peak stages of Allen remain a separate art/feat review.
- **S:** Original-costume Mark, Thula, Anissa and Lucan. Current ordinary Mark **is not endgame Mark**.
- **A:** Standard Atom Eve (with mental blocks), Immortal, Universa, Bulletproof, Angstrom Levy (portal hax). Eve's exceptional condition belongs in its own pending card.
- **B–C:** Monster Girl, Powerplex, Rex Splode, Titan, Dupli-Kate and the *single-suit* Robot edition (not his whole army).
- **F:** Cecil Stedman: direct unassisted physical combat only. His control of the GDA is not a fistfight stat.
- **EX:** No automatic assignment. Needs demonstrated exception **beyond SSS** for a specific canonical form and matching image. User can review separately.

### Source and QA policy

Fighter illustrations: https://invinciblevs.com/fighters; canon: https://imagecomics.com/comics/series/invincible; supplemental source for Battle Beast / Thragg duel: https://comicbook.com/comics/list/the-10-most-powerful-members-of-the-invincible-universe-ranked/.

Every acquired original image is stored untouched in `artwork/invincible/original/` with SHA-256, native dimensions and the exact source URL. The renderer-safe PNG is an **aspect-preserving resize**, never represented as new native-resolution detail. No substituting different costumes or different people's artwork. Source change invalidates review. Actual 3000 × 4800 PNGs, SVGs, phone proofs and searchable gallery remain candidate build outputs until examined at both full and 320px.

**Copyright:** official images, logos, characters and their design are rights-reserved. These are internal research/prototype candidates; public/card-bot distribution and monetization require separate rights review. Nothing is approved merely because Actions passes. No changes to the live main Cards site or WhatsApp bot.

### Developer workflow

On the dedicated branch, GitHub Actions `cards-invincible.yml` fetches originals, generates source pins and source-specific ranking records, tests identity and transforms, renders all candidates using the existing NIGHT exporter, and uploads masters, actual phone proofs and an offline gallery. Review failures and missing/unavailable artwork are explicit blockers and must not be filled with placeholders.
