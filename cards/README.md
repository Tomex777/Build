# NIGHT / BLEACH collection

A reusable, programmed collectible-card library. The renderer is preserved in `renderer/`, including the original Obsidian PNG, SVG and source in `renderer/preserved/obsidian-v1/`. The restored Nocturne has no circular backdrop; Nocturne and its pearl counterpart Tidelight omit the old emblem.

Each character/form has pinned original artwork and a composition recipe. Batch builds export 3000×4800 PNG masters, embedded SVGs, 320×512 phone proofs, contact sheets, an offline searchable gallery and a machine-readable manifest. These are display cards with no combat statistics.

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
npm run approve -- --ids aizen-featured--nocturne,aizen-featured--obsidian --reviewer YourName
```

Review records live in `reviews/bleach.json`. Changes to artwork, crops, identities, render code, fonts or dependency lock invalidate the fingerprint. CI never grants visual approval.

## Bot use

```js
import {openCardLibrary} from './cards/src/bot.mjs';
const cards = await openCardLibrary('/srv/night/cards/bleach');
const selected = await cards.image('Ichigo', {
  form: 'original-shikai', design: 'nocturne'
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

The eight finishes remain available: Vanguard, Ivory, Voltage, Crimson, Emberglass, Obsidian, Tidelight and Nocturne. The proposed tier mapping is preserved in `renderer/examples/ichigo-collection-proposal.json`; this work does not set the bot's drop rates, pricing or trading economy.

The phone proof is a 320px display check. It is not an actual WhatsApp transmission/compression test.
