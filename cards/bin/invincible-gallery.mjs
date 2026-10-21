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
// The shared baseline gallery searches legal names only. Include distinct hero
// aliases here: "Battle Beast" must find Thokk, "Omni-Man" must find Nolan.
const searchOld="c.name+' '+c.form+' '+c.design+' '+c.power.rank";
const searchNew="c.name+' '+(c.title||'')+' '+(c.aliases||[]).join(' ')+' '+c.form+' '+c.design+' '+c.power.rank";
const viewerOld="put('viewer-name',c.name);";
const viewerNew="put('viewer-name',c.title&&c.title!==c.name?c.title+' · '+c.name:c.name);";
if(!html.includes(searchOld)||!html.includes(viewerOld))throw Error('Shared gallery search/viewer markup changed; review adapter.');
html=html.replace(searchOld,searchNew).replace(viewerOld,viewerNew);
await atomic(file,html);
console.log('Relabeled existing NIGHT gallery:',file);
