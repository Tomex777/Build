#!/usr/bin/env node
// Text-only series label adapter. Actual art/rendering uses NIGHT's universal renderer.
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
const file=inside(process.argv[2]??'dist/invincible/index.html');
let html=await fs.readFile(file,'utf8');
if(!html.includes('NIGHT / BLEACH'))throw Error('Original shared gallery label changed; inspect before adapting');
html=html.replaceAll('NIGHT / BLEACH','NIGHT / INVINCIBLE')
 .replaceAll('BLEACH · ','INVINCIBLE · ')
 .replaceAll('within BLEACH','within Invincible')
 .replaceAll('forms within BLEACH','forms within Invincible');
await atomic(file,html);
console.log('Relabeled existing NIGHT gallery:',file);
