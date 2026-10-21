#!/usr/bin/env node
// Adapt only the collection UI labels: all card graphics come untouched from
// the universal NIGHT renderer, never from a franchise-specific CSS mock-up.
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
const file=inside(process.argv[2]??'dist/maou-gakuin/index.html');
let html=await fs.readFile(file,'utf8');
if(!html.includes('NIGHT / BLEACH'))throw Error('Gallery source label changed; inspect shared exporter.');
html=html.replaceAll('NIGHT / BLEACH','NIGHT / MAOU GAKUIN')
 .replaceAll('BLEACH · ','MAOU GAKUIN · ')
 .replaceAll('within BLEACH','within The Misfit of Demon King Academy')
 .replaceAll('forms within BLEACH','forms within The Misfit of Demon King Academy');
await atomic(file,html);
console.log('Relabeled Maou Gakuin candidate gallery:',file);
