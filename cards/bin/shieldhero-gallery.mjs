#!/usr/bin/env node
// Localize the existing universal NIGHT collectible gallery after exporting
// actual shared-renderer masters. No HTML/CSS mock cards or new frame system.
import fs from 'node:fs/promises';
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
console.log('Shield Hero gallery identity set; card images remain renderer originals');
