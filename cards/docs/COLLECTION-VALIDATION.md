# Collection validation — 2026-10-19

85 editions, 76 characters and 85 distinct pinned original illustration SHA-256 values. The proposed tier mapping is F/Vanguard, E/Ivory, D/Voltage, C/Crimson, B/Tidelight, A/Nocturne, S/Emberglass and SS/Obsidian. F has no acquired artwork; SSS and EX have no assigned cards or approved designs. Parallax is excluded.

## Completed checks

- All 85 masters rendered at 3000×4800 using pinned Chrome for Testing 131.0.6778.204, with zero quarantines.
- All 85 SVGs independently regenerated from current artwork, recipes, masks, form assessments and renderer; their SHA-256 values match the saved SVGs. Master PNG hashes and dimensions match. Phone PNGs were independently re-derived from the masters and match byte-for-byte. Gallery preview hashes and all review fingerprints match.
- Audit renderer digest: `663e9331521b5769b7400dfdec88fa7f6de77bf631c8c755047bd1ac144bd1a6`. Audit completed at `2026-10-19T14:54:09.017Z`.
- 18 automated tests pass. Coverage includes exact form/source binding, in-universe grade consistency, continuity requirements, SSS/EX evidence requirements, distinct edition sources, proposed tier/design matching, source-bound masks preserving original pixels, glass glyphs without a plate, approval invalidation and bot integrity guards.
- The real-browser breakout regression passes: the actual foreground crosses the inner frame; shrinking that same foreground inside it is rejected even while the separate full-size aura remains. Every acquired master passes the configured continuous crossing threshold of 24. This is an alpha-based guard, not a substitute for identifying physical character pixels visually.
- Both the relative-file and standalone galleries pass offline at 360×800 and 1024×768. Search finds five Ichigo cards; tier combinations, empty results, image decoding, viewer navigation, arrow keys, Escape, wraparound and reference links work. Zero broken images, page script errors or external asset requests were observed.

## Visual review

All 85 phone proofs were inspected across nine contact sheets. The masters were checked through fifteen native-pixel detail boards and the complete phone compositions. The six new foreground mattes and the changed placements received particular attention. Classic Kenpachi's original source ends at the upper crop; a source-edge feather softens that boundary without inventing missing hair. Byakuya's pink background fringe was removed from the foreground matte.

The separate 3000×4800 Crimson Kenpachi material comparison was checked as a full composition, at native head/frame and sleeve/frame details, and at 320×512. His real proposed A grade is preserved. This comparison is not another active edition or a C-grade assignment.

The seven occupied designs are presented together in the tier review sheet. The offline HTML contains the complete current collection. Glass rank letters and the accepted 84% title scale are preserved. Archived renderer files and the original Obsidian archive have no modifications.

These are review candidates. Existing form grades remain editorial proposals within BLEACH, not official statistics or a new exhaustive canonical feat audit. Human Ichigo and other missing forms remain pending matched artwork. No new visual approvals, production publication or bot deployment were performed.

## Reproduce

```bash
npm ci
npm test
NIGHT_BROWSER_PATH=/path/to/pinned/chrome npm run test:breakout
NIGHT_BROWSER_PATH=/path/to/pinned/chrome npm run build -- --out dist/bleach-refined-masters
npm run gallery -- --out dist/bleach-refined-masters --file dist/Bleach-Collection.html
NIGHT_BROWSER_PATH=/path/to/pinned/chrome npm run test:gallery
node bin/audit-masters.mjs
python3 bin/review-details.py
NIGHT_BROWSER_PATH=/path/to/pinned/chrome node bin/crimson-proof.mjs
python3 bin/tier-review.py
```

Python review utilities use Pillow. The existing matte authoring helper additionally uses its documented pinned OpenCV/NumPy packages; committed mattes can be rendered without those optional authoring dependencies.
