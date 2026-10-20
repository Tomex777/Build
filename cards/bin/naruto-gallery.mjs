#!/usr/bin/env node
// Apply Naruto identity to the shared-renderer gallery. Never generate mock
// cards, alter BLEACH source templates, or grant approval to these candidates.
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
const file=inside(process.argv[2]??'dist/naruto/index.html');
let html=await fs.readFile(file,'utf8');
if(!html.includes('NIGHT / BLEACH'))throw Error('Shared gallery labels changed; inspect adapter');
html=html.replaceAll('NIGHT / BLEACH','NIGHT / NARUTO')
 .replaceAll('BLEACH · ','NARUTO · ')
 .replaceAll('within BLEACH','within Naruto');
await atomic(file,html);
console.log('NIGHT Naruto renderer gallery labeled:',file);
