import fs from 'node:fs/promises';
import path from 'node:path';
import {ROOT} from '../src/node-assets.mjs';
import {characterCards} from '../src/characters.mjs';
import {loadCharacterAssets} from '../src/character-assets.mjs';
import {renderCard} from '../src/index.mjs';
import {createRasterizer} from '../src/raster.mjs';

const profileFile = path.resolve(process.argv[2] ?? path.join(ROOT,'examples/characters/naruto.json'));
const out = path.resolve(process.argv[3] ?? path.join(ROOT,'dist/naruto'));
const width = Number(process.argv[4] ?? 3000);
if (!Number.isInteger(width) || width < 160 || width > 4000) throw new Error('Width must be an integer from 160 to 4000');
const profile = JSON.parse(await fs.readFile(profileFile,'utf8'));
const templates = JSON.parse(await fs.readFile(path.join(ROOT,'examples/cards.json'),'utf8'));
const cards = characterCards(templates,profile);
const assets = await loadCharacterAssets(profile);
await fs.mkdir(out,{recursive:true});
await fs.writeFile(path.join(out,'cards.json'),JSON.stringify(cards,null,2));
const raster = await createRasterizer();
try {
 for (const c of cards) {
  const svg = renderCard(c,assets);
  const png = await raster.render(svg,{width});
  await fs.writeFile(path.join(out,`NIGHT-${c.id}.svg`),svg);
  await fs.writeFile(path.join(out,`NIGHT-${c.id}-${width}.png`),png);
  console.log(`Rendered ${c.id}: ${width} × ${Math.round(width*1.4)}`);
 }
} finally {await raster.close();}
await fs.writeFile(path.join(out,'ARTWORK.txt'),`${assets.title}\n${assets.rights ?? `${assets.artist} · ${assets.license}`}\nSource: ${assets.sourceUrl}\nOriginal image: ${assets.imageUrl ?? assets.sourceUrl}\nNative artwork: ${assets.artWidth} × ${assets.artHeight}; card export: ${width} × ${Math.round(width*1.4)}.\nCard frames, materials and typography are the unchanged original templates.\n`);
