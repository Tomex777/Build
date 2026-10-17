import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {chromium} from 'playwright';
import {characterCards} from '../src/characters.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {renderPortraitCard} from '../src/portrait.mjs';
import {renderRestoredCard} from '../src/restored.mjs';

const baseline = JSON.parse(await fs.readFile('qa/noctis-preserved-baseline.json', 'utf8'));
for (const [file, hash] of Object.entries(baseline))
  assert.equal(crypto.createHash('sha256').update(await fs.readFile(file)).digest('hex'), hash, file);
const browser = await chromium.launch({headless: true, executablePath: process.env.NIGHT_BROWSER_PATH ?? '/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell', args: ['--no-sandbox']});
const checks = [];
try {
  const page = await browser.newPage();
  const templates = JSON.parse(await fs.readFile('examples/cards.json', 'utf8'));
  for (const character of ['ichigo', 'naruto']) {
    const profile = JSON.parse(await fs.readFile(`examples/characters/${character}.json`, 'utf8'));
    const assets = await loadCharacterAssets(profile);
    for (const card of characterCards(templates, profile).filter(c => c.theme !== 'verdant'))
      for (const side of ['front', 'back']) {
        const options = {side, prefix: `compare-${character}-${card.theme}-${side}-`};
        const before = renderPortraitCard(card, assets, options);
        const after = renderRestoredCard(card, assets, options);
        const result = await page.evaluate(({before, after, theme, side}) => {
          const parse = text => new DOMParser().parseFromString(text, 'image/svg+xml');
          const old = parse(before), updated = parse(after);
          if (old.querySelector('parsererror') || updated.querySelector('parsererror')) throw Error('Invalid SVG');
          if (theme === 'nocturne') {
            const oldDisc = [...old.querySelectorAll('ellipse')].find(e =>
              e.getAttribute('cx') === (side === 'front' ? '525' : '500') &&
              !e.closest('defs'));
            if (!oldDisc) throw Error('Expected original disc');
            const group = oldDisc.parentElement;
            (side === 'back' ? group.parentElement : group).remove();
            if ([...updated.querySelectorAll('ellipse')].some(e => !e.closest('defs')))
              throw Error('A visible circular backdrop remains');
          }
          updated.documentElement.removeAttribute('data-restored');
          updated.documentElement.removeAttribute('data-circular-disc');
          const serialize = doc => new XMLSerializer().serializeToString(doc);
          if (serialize(old) !== serialize(updated)) throw Error('Unexpected change outside the requested disc');
          const ids = [...updated.querySelectorAll('[id]')].map(n => n.id);
          if (new Set(ids).size !== ids.length) throw Error('Duplicate SVG ids');
          const refs = [...after.matchAll(/url\(#([^\)]+)\)|(?:href)="#([^"]+)"/g)].map(m => m[1] ?? m[2]);
          if (refs.some(id => !ids.includes(id))) throw Error('Missing SVG paint/image reference');
          if (updated.querySelector('g[transform="translate(879 1190)"]')) throw Error('Old emblem remains');
          return {exactRequestedChange: true, uniqueIds: true, validReferences: true, emblemRemoved: true};
        }, {before, after, theme: card.theme, side});
        checks.push({character, design: card.theme, side, ...result});
      }
  }
} finally {await browser.close();}
await fs.mkdir('qa/restored-final', {recursive: true});
await fs.writeFile('qa/restored-final/verification.json', JSON.stringify({preservedFiles: Object.keys(baseline).length, checks}, null, 2));
console.log(`Verified ${checks.length} character/design/side combinations; ${Object.keys(baseline).length} original files unchanged.`);
