#!/usr/bin/env node
// Correct labels in the offline Re:Zero candidate gallery without changing
// production BLEACH renderer files or invalidating their approved fingerprints.
import fs from 'node:fs/promises';
import path from 'node:path';
import {inside,atomic} from '../src/catalogue.mjs';
const file=inside(process.argv[2]??'dist/ReZero-Collection.html');
let html=await fs.readFile(file,'utf8');
if(!html.includes('NIGHT / BLEACH'))throw Error('Upstream gallery label changed; inspect before rewriting');
html=html.replaceAll('NIGHT / BLEACH','NIGHT / RE:ZERO')
  .replaceAll('BLEACH · ','RE:ZERO · ')
  .replaceAll('within BLEACH','within Re:Zero')
  .replaceAll('forms within BLEACH','forms within Re:Zero')
  .replaceAll('NIGHT editorial grade for this form within BLEACH','NIGHT editorial grade for this form within Re:Zero')
  .replaceAll('Other universes are ranked independently','Other universes are ranked independently');
await atomic(file,html);
console.log('Corrected Re:Zero gallery identity:',path.relative(inside('dist'),file));
