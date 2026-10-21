#!/usr/bin/env node
// Update collection-specific labels in the offline KonoSuba gallery.
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
const file=inside(process.argv[2]??'dist/KonoSuba-Collection.html');
let html=await fs.readFile(file,'utf8');
if(!html.includes('NIGHT / BLEACH'))throw Error('Upstream shared gallery changed: inspect before editing');
html=html.replaceAll('NIGHT / BLEACH','NIGHT / KONOSUBA')
.replaceAll('BLEACH · ','KONOSUBA · ')
.replaceAll('within BLEACH','within KonoSuba')
.replaceAll('forms within BLEACH','forms within KonoSuba')
.replaceAll('Other universes are ranked independently','Other anime universes are ranked independently');
await atomic(file,html);
console.log('Relabelled shared NIGHT gallery for KonoSuba (unchanged designs).');
