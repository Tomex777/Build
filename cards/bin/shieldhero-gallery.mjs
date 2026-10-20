#!/usr/bin/env node
// Localize the existing universal NIGHT collectible gallery after exporting
// actual shared-renderer masters. No HTML/CSS mock cards or new frame system.
import fs from 'node:fs/promises';
import sharp from 'sharp';
import path from 'node:path';
import {inside,atomic} from '../src/catalogue.mjs';
const file=inside(process.argv[2]??'dist/ShieldHero-Collection.html');
let html=await fs.readFile(file,'utf8');
if(!html.includes('NIGHT / BLEACH'))throw Error('Source gallery identity changed: inspect renderer first');
html=html.replaceAll('NIGHT / BLEACH','NIGHT / SHIELD HERO')
  .replaceAll('BLEACH · ','SHIELD HERO · ')
  .replaceAll('within BLEACH','within Shield Hero')
  .replaceAll('forms within BLEACH','forms within Shield Hero')
  .replaceAll('Other universes are ranked independently','Other universes are ranked independently');
await atomic(file,html);
// Correct the source renderer's BLEACH-only text on contact sheets without
// altering the shared renderer, card designs, or approval fingerprints.
const images=await fs.readdir(inside('dist/shieldhero'));
const banner=Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="560" height="76"><rect width="560" height="76" fill="#10151a"/><text x="32" y="47" font-family="Arial,sans-serif" font-size="25" fill="#f0e7d5">NIGHT / SHIELD HERO</text></svg>');
for(const name of images.filter(n=>/^contact-\\d+\\.png$/.test(n))){
  const dest=inside(path.join('dist/shieldhero',name));
  const img=await sharp(await fs.readFile(dest)).composite([{input:banner,left:0,top:0}]).png().toBuffer();
  await atomic(dest,img);
}
console.log('Shield Hero gallery and contact sheets localized; no card designs changed');
