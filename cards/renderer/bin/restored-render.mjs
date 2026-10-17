import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import sharp from 'sharp';
import {characterCards} from '../src/characters.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {renderRestoredCard} from '../src/restored.mjs';
import {createRasterizer} from '../src/raster.mjs';

const profilePath = process.argv[2] ?? 'examples/characters/ichigo.json';
const out = path.resolve(process.argv[3] ?? 'dist/restored');
const width = Number(process.argv[4] ?? 3000);
const profile = JSON.parse(await fs.readFile(profilePath, 'utf8'));
const templates = JSON.parse(await fs.readFile('examples/cards.json', 'utf8'));
const cards = characterCards(templates, profile).filter(c => c.theme !== 'verdant');
const assets = await loadCharacterAssets(profile);
const local = '/tmp/night-browser/chrome-headless-shell-linux64/chrome-headless-shell';
const executablePath = process.env.NIGHT_BROWSER_PATH ?? await fs.access(local).then(() => local, () => undefined);
const engine = await createRasterizer({executablePath, timeout: 120000});
await fs.mkdir(out, {recursive: true});
const records = [];
try {
  for (const card of cards) {
    const svg = renderRestoredCard(card, assets);
    const png = await engine.render(svg, {width});
    const image = sharp(png), meta = await image.metadata();
    await image.raw().toBuffer();
    if (meta.width !== width || meta.height !== width * 1.6) throw new Error('Unexpected export dimensions');
    const stem = `${profile.id}-${card.theme}-restored`;
    for (const [ext, bytes] of [['svg', svg], ['png', png]]) {
      const file = path.join(out, stem + '.' + ext);
      await fs.writeFile(file + '.pending', bytes);
      await fs.rename(file + '.pending', file);
    }
    const phone = await sharp(png).resize(320, 512).png().toBuffer();
    await fs.writeFile(path.join(out, stem + '-phone.png'), phone);
    records.push({id: card.id, design: card.theme, png: stem + '.png', svg: stem + '.svg', width, height: meta.height,
      sha256: crypto.createHash('sha256').update(png).digest('hex')});
    console.log(`${card.theme}: ${meta.width} × ${meta.height}`);
  }
  await fs.writeFile(path.join(out, 'renders.json'), JSON.stringify({character: profile.id, source: assets.sourceUrl,
    rights: assets.rights, nativeArtwork: [assets.artWidth, assets.artHeight], exports: records}, null, 2));
} finally {await engine.close();}
