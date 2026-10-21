# NIGHT romance composition study

These cards reuse `renderRestoredCard` and `frameDepth`, with two separately
identified original character PNGs. The preserved renderer is unchanged. There
is no image generation or AI upscaling. Relationship labels replace power grades.
Original portrait scale compensation is retained so subjects are not stretched.

`artwork/romance/sources.json` records the exact bytes, SHA-256, native dimensions,
source page, rights, crop and alpha bounds. Rights remain with the original
owners. A 3000×4800 export does not add native illustration detail.

Run `node bin/render-romance.mjs` with `NIGHT_BROWSER_PATH` set to the existing
Chrome executable. Outputs include embedded SVGs, PNG masters, 320×512 phone
proofs, an offline HTML collection and a source manifest. The build verifies
source checksums, identities, title bounds, absent power grades, and each
character's alpha crossing separately. Inspect actual masters, native details
and phone proofs before recording a visual review.

The current recovered sample is Taiki/Chinatsu. Recipes for Tomo/Jun and
Rintaro/Kaoruko are retained under requestedCards; their source servers are
returning the official emergency-maintenance page on October 21. Matching
Gong-ja/Raviel romance-arc artwork remains pending. Only acquired and rendered
cards are included in the exported gallery. This study does not publish to
the main gallery or deploy the bot.
