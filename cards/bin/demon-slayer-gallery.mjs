#!/usr/bin/env node
// Change catalogue wording only; keep the exact shared NIGHT renderer.
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
const target=inside(process.argv[2]??'dist/DemonSlayer-Collection.html');
let html=await fs.readFile(target,'utf8');
if(!html.includes('NIGHT / BLEACH'))throw Error('Shared gallery template changed; review required');
html=html.replaceAll('NIGHT / BLEACH','NIGHT / DEMON SLAYER')
  .replaceAll('BLEACH · ','DEMON SLAYER · ')
  .replaceAll('within BLEACH','within Demon Slayer')
  .replaceAll('forms within BLEACH','forms within Demon Slayer')
  .replaceAll('Ranks compare forms within BLEACH','Ranks compare forms within Demon Slayer');
await atomic(target,html);
console.log('Demon Slayer candidate gallery uses shared NIGHT card design');
