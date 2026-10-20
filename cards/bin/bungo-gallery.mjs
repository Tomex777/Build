#!/usr/bin/env node
// Reuse shared NIGHT visual exports; only the universe's gallery labels change.
// This is never a standalone CSS mockup.
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
const file=inside(process.argv[2]??'dist/Bungo-Stray-Dogs-Collection.html');
let html=await fs.readFile(file,'utf8');
if(!html.includes('NIGHT / BLEACH'))throw Error('Upstream gallery marker changed: inspect the shared renderer');
html=html.replaceAll('NIGHT / BLEACH','NIGHT / BUNGO STRAY DOGS')
  .replaceAll('BLEACH · ','BUNGO STRAY DOGS · ')
  .replaceAll('within BLEACH','within Bungo Stray Dogs')
  .replaceAll('forms within BLEACH','forms within Bungo Stray Dogs')
  .replaceAll('Other universes are ranked independently','Other universes are ranked independently');
await atomic(file,html);
console.log('NIGHT/Bungo Stray Dogs shared gallery label corrected');
