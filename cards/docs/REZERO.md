# NIGHT Cards — Re:Zero anime collection

**Status: artwork-acquisition and render candidates.** Not visually approved, not licensed for commercial distribution, not deployed into the live NIGHT bot.

This expansion keeps the established NIGHT card architecture: genuine original character cutouts break beyond the card's **inner** frame, while effects stay behind the frame. Every completed master should be 3000×4800 PNG with 320×512 phone proof, provenance, glass tier lettering and a searchable offline gallery. BLEACH renderer and existing cards remain unchanged.

## Cast and forms

`catalog/rezero-sources.json` defines **38 candidate character/form editions**. Multiple forms have **distinct images**, especially Rem's standard combat-capable maid portrait versus her visibly unconscious wheelchair portrait, and Ram's manor versus travelling outfit. The two Rem states deliberately have different power ranks. Garfiel's human portrait is never relabelled as tiger form.

The initial character roster covers Subaru, Emilia, Rem, Ram, Beatrice, Roswaal, Felt, Reinhard, Crusch, Felix, Wilhelm, Anastasia, Julius, Priscilla, Al, Elsa, Petelgeuse, Frederica, Petra, Otto, Meili, Garfiel, Ryuzu, Echidna, Minerva, Daphne, Typhon, Sekhmet, Carmilla, Regulus, Liliana, Sirius, Joshua, Kiritaka, Heinkel and Capella.

Character portraits are from the [official Re:Zero anime character page](https://re-zero-anime.jp/tv/character/). Rights are reserved by their holders; recording a source is **not** a license. A generated PNG is not new native-resolution source detail. Keep candidate previews private until permission is resolved for wider circulation.

## Reproducible workflow

```sh
cd cards
npm ci
node bin/seed-rezero.mjs --write-lock
npm test
# Requires the reference Chrome 131 binary documented in cards/README.md
npm run build -- --series rezero --width 3000 --out dist/rezero
npm run gallery -- --series rezero --out dist/rezero --file dist/ReZero-Collection.html
node bin/rezero-gallery.mjs dist/ReZero-Collection.html
```

The seed pins **original-file SHA-256 and native dimensions** in `catalog/rezero-source-lock.json`, `catalog/rezero.json` and `catalog/power/rezero.json`; bytes are kept in ignored `artwork/rezero/`, not committed. Subsequent runs refuse any upstream source change unless the operator explicitly supplies `--refresh-pins --write-lock` after inspecting the replacement. Distinct art is required per edition.

GitHub workflow `.github/workflows/cards-rezero.yml` executes the same acquisition, tests, full-size rendering and gallery export. It uploads an artifact **NIGHT-ReZero-Candidate-Cards** and commits successful source pin metadata. It does **not** approve artworks or deploy them into the bot. The code can render only sourced assets it successfully verifies; a failed asset is not disguised by unrelated character art.

## Power tier proposals

Relative F–EX ranks apply **only within the Re:Zero anime continuity**; they cannot be interpreted as equal to Bleach or Solo Leveling values. Re:Zero in-world offices (Sin Archbishop, Sword Saint, Witch, Royal Candidate) are not NIGHT grades. The editorial grade uses a form's demonstrable personal offence, defence, speed, skills, limitations, counters and conditional Authority mechanics. Subaru's Return by Death is not a claim of high direct battle output. Regulus and Reinhard are SS proposals, **not** automatically EX. SSS and EX require independently checked, source-located feat records and therefore remain unassigned.

An outfit does not imply unseen transformations. A character's political status, foresight, healing, animal communication or mabeast-control utility should not silently inflate physical attack power. These are **proposals** until human lore review.

## Artwork backlog — not secretly finished

- Puck: official pet-size sprite needs a character-specific close-up composition to satisfy the real break-out rule.
- Satella, Pandora and other not-yet-sourced characters: source their correct official cutouts before adding real catalogue records; don't label a lookalike as them.
- Emilia advanced awakening, Garfiel transformed tiger, Rem oni-horn combat, Reinhard alternate Sword Saint poses and other significant form variations require distinct matching official art. Never reuse the same original file for two collectible editions.
- Pending visual inspection: inspect actual exported masters, native-pixel facial/hair/hand edges and 320px proofs; fix cases where only hair aura or a background glows beyond the border. Source + fingerprint + review required before bot publishing.

Re:Zero belongs alongside the existing collections; a new candidate gallery does not overwrite Bleach, nor certify all 38 designs as approved.
