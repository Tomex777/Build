# Collection validation - 2026-10-18

- 85 editions, 76 characters and 85 unique original illustration SHA-256 values.
- All 85 masters rendered at 3000 x 4800 with pinned Chrome for Testing 131.0.6778.204; no quarantines.
- Master PNG hashes and dimensions checked; all 85 SVGs regenerated from current source, recipes, masks and form grades and compared byte-for-byte via SHA-256 with the rendered SVGs. Current renderer fingerprints match.
- 16 automated tests pass, including source/form binding, same-form grade consistency, no repeated edition artwork, glass lettering without a plate, depth-layer provenance, approval invalidation and bot integrity guards.
- Both relative-file and standalone galleries pass offline at 360 x 800 and 1024 x 768. Search finds all five Ichigo cards. Combined finish/rank filters, empty states, viewer images, previous/next wrap, arrow keys, Escape and source links work. No broken images, page script errors or external asset requests.
- All 85 phone proofs inspected across nine boards. The seventeen changed illustration/composition records received enlarged master-composition and native-pixel edge/detail checks; the accepted Yoruichi Nocturne composition was rechecked. These checks are for collection judgment, not a blanket production publication approval.
- The 15-page review book contains all 85 editions and 85 working source-link annotations. Pages were rasterised and inspected, including the complete page overview and selected full pages.
- Glass letter styling and the 84% main title scale remain intact. The archived renderers and Obsidian archive have no modifications.

Ranks remain NIGHT editorial proposals for the depicted forms. Human Ichigo remains pending matched artwork, and the duplicate Ulquiorra Tidelight edition is pending a distinct unreleased-form illustration. No bot deployment or production publication was performed.

Re-run gallery browser checks after a complete build/export with:

```bash
NIGHT_BROWSER_PATH=/path/to/pinned/chrome-headless-shell npm run test:gallery
```
