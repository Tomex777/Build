# NIGHT / BLEACH collection

A reusable, programmed collectible-card library. The renderer is preserved in `renderer/`, including the original Obsidian PNG, SVG and source in `renderer/preserved/obsidian-v1/`. The restored Nocturne has no circular backdrop; Nocturne and its pearl counterpart Tidelight omit the old emblem.

Each character/form has pinned original artwork and a composition recipe. Batch builds export 3000×4800 PNG masters, embedded SVGs, 320×512 phone proofs, contact sheets, an offline searchable gallery and a machine-readable manifest. Cards now show compact translucent glass rank letters at the top right, with the main name reduced to 84% of its previous size. The preserved renderers and original Obsidian archive remain reproducible.

## Run

Use Node 24 and Python 3 (packaging uses only the Python standard library).

```sh
cd cards
npm ci
mkdir -p .cache
curl -fL https://storage.googleapis.com/chrome-for-testing-public/131.0.6778.204/linux64/chrome-headless-shell-linux64.zip -o .cache/chrome.zip
unzip -q .cache/chrome.zip -d .cache/browser
chmod +x .cache/browser/chrome-headless-shell-linux64/chrome-headless-shell
npx playwright install-deps chromium
npm test
npm run build
npm run publish
npm run verify
npm run pack
```

The GitHub Actions workflow performs the same build on the `cards` branch and uploads the completed collection ZIP. Linux is the reference export environment; `NIGHT_BROWSER_PATH` can point to the pinned Chrome executable elsewhere. The exact browser version is checked.

`dist/candidates/index.html` is the build gallery; `dist/published` is the complete approved bot library. A study can be built without replacing published assets:

```sh
npm run build -- --width 1000 --out dist/study --only aizen-featured,ulquiorra-featured
```

## Unattended operation

The approved catalogue rebuilds unattended: source checksums, transparent crops, safe focal points, fonts, title bounds, SVG references, resolution, full PNG decoding, and review fingerprints are checked. Unchanged builds resume from verified outputs. Failures are quarantined and prevent publication. The full catalogue is required for publication, so a study cannot overwrite the collection with a subset.

Automatic discovery finds new candidates, not approved cards. A new pose still needs composition review. The system does not pretend a numeric score can guarantee taste on every unseen image. Once a recipe is approved, the bot can reuse it indefinitely without choosing artwork again.

After visually inspecting the actual 3000×4800 masters, native-pixel details and 320px proofs, record only the specific reviewed edition IDs:

```sh
npm run approve -- --ids aizen-featured--emberglass,byakuya-senbonzakura--nocturne --reviewer YourName
```

Review records live in `reviews/bleach.json`. Changes to artwork, crops, identities, render code, fonts or dependency lock invalidate the fingerprint. CI never grants visual approval.

## Bot use

```js
import {openCardLibrary} from './cards/src/bot.mjs';
const cards = await openCardLibrary('/srv/night/cards/bleach');
const selected = await cards.image('Ichigo', {
  form: 'original-shikai', design: 'tidelight'
});
// Supply selected.buffer and selected.caption to the existing media sender.
```

No network search or rendering happens during a bot message. Ambiguous requests return `CARD_AMBIGUOUS` with exact choices; missing forms return `CARD_NOT_FOUND`. Served PNG bytes are checked against the published manifest. This package is ready for integration; it does not modify or deploy the existing `.card` command.

## Artwork discovery and rights

```sh
npm run discover -- --provider brave-souls
npm run discover -- --provider bleach-anime
npm run discover -- --provider commons --query "illustration" --limit 12
npm run discover -- --provider krita --limit 12
```

The open-license discovery/acquisition implementation is retained in `renderer/src/discovery.mjs`: explicit CC BY, CC BY-SA, CC0 or public-domain metadata, artist attribution, native resolution checks, provider allowlists and fresh rights verification before acquisition. It rejects noncommercial, no-derivatives, restricted and unidentified-author records. A filename alone never supplies a license.

BLEACH artwork in this character collection comes from the official Brave Souls, Soul Resonance and anime sites. It is **rights reserved and recorded as reference-only**. The renderer's MIT license does not grant BLEACH merchandise or commercial distribution rights. Source URLs and rights remain attached to every export. Native source illustrations are generally 750×1400, 1050×1500 or 1920×1080; a 3000×4800 export does not create native 4K illustration detail. No AI generation or AI upscaling is used.

The seed script is a reproducible first-import tool and refuses to overwrite an existing catalogue. Later additions should use explicit new records and reviewed recipes. Official anime/game art is never silently replaced with search thumbnails.

## Forms and tiers

Ichigo's substitute, original Shikai and original Bankai illustrations are separate records. Other forms remain in the proposal until matching art is acquired. Blood Chains is not fabricated using Shikai artwork. TYBW portraits are labeled for their source arc; general game portraits are not assigned an unverified transformation.

The eight finishes remain available: Vanguard, Ivory, Voltage, Crimson, Emberglass, Obsidian, Tidelight and Nocturne. The working tier proposal selects a design from the form’s power grade. This arrangement is for review; it has not been accepted as a production tier system. The superseded rarity/finish proposal is preserved unchanged in `renderer/examples/ichigo-collection-proposal.json`; active pending-form records no longer use it.

### In-universe power scale

`src/power.mjs` defines independent form-specific assessments within each universe: **F → E → D → C → B → A → S → SS → SSS → EX**. It considers combat strength, speed, defence, special abilities, conditions and limitations. Fame, finish and drop rates do not determine the grade. See [the power-ranking notes](docs/POWER-RANKS.md) for the benchmarks and remaining assessment limits.

`catalog/power/bleach.json` holds explicit, **proposed NIGHT editorial assessments**, not official power levels or a completed scene-by-scene feat audit. Every existing entry has a separate assessment bound to its form ID and artwork checksum. Human Ichigo has the lowest grade, F, but stays pending until matching artwork is acquired. Other unassessed future forms keep a null rank; the system never silently assigns them F or imports a later transformation's power.

The rank is included in review fingerprints, export metadata, gallery filtering and bot lookup (`{rank:'A'}`). Changing a form, source or grade invalidates its approval. The gallery starts with the strongest proposed grades and combines search with a rank filter. The rank glyphs themselves refract the actual scene and use translucent faces with fine bevel highlights. They float over the artwork without a box, pill, border or background plate. The two decorative four-point sparkle icons are omitted from new Nocturne/Tidelight exports. Character depth layers remain intact.

This work does not set the bot's drop rates, pricing or trading economy, and does not deploy NIGHT/MSCC. Composition changes require new visual review; old approvals are intentionally not copied to changed cards.

The phone proof is a 320px display check. It is not an actual WhatsApp transmission/compression test.

Yoruichi's Nocturne recipe separates her silhouette from the baked-in aura. `recipe.depthLayers.nocturne` pins a vector mask to the original source checksum and exact crop. The isolated original character pixels receive the existing foreground shadow and overlap; the dimmed aura is clipped inside the rear architectural opening. `cropEdgeFade` feathers only the cut canvas boundaries. The same source-bound matte mechanism supports all eight inner-frame geometries. Changing the mask checksum, opacity or recipe requires a new visual review.

## Collection-wide review

The active catalogue now has 85 editions and 85 distinct pinned original illustrations across 76 characters. Seven repeated-art slots have new illustrations, sixteen additional sources use separate foreground mattes, and every acquired form has explicit power boundaries. The extra Ulquiorra Tidelight edition is pending matching distinct artwork. See [the review](docs/COLLECTION-REVIEW.md) for changed grades, sources and per-form exclusions.

Run `npm run gallery -- --out dist/bleach-refined-masters --file dist/Bleach-Collection.html` after a complete current build to create a self-contained searchable gallery. Exporting a gallery does not approve or publish the bot library.

Create the PDF review book after a full build with `python3 bin/review-book.py dist/bleach-refined-masters output/pdf/Bleach-Collection.pdf` (ReportLab required). The HTML export uses compressed embedded copies for offline viewing; lossless phone proofs and 3000px masters stay in the build folder.

## Current refinement

Every active card must place real character pixels across its inner frame. The outer edge is print trim. `npm run test:breakout` checks that a contained figure cannot pass merely because its separate aura extends past the frame; manual inspection is still required for artwork with baked-in effects. Foreground masks and per-design placements preserve the original illustration pixels.

Crimson now uses muted oxblood and dark steel through `src/crimson.mjs`. Archived renderers are unchanged. [The tier plan](docs/TIER-PLAN.md) records the reviewable mapping, occupied tiers, pending human artwork and open SSS/EX designs. [Validation](docs/COLLECTION-VALIDATION.md) records the completed checks. No production publication or bot deployment is included.
