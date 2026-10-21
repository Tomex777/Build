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
// Embed the identity aliases into each gallery record: the shared exporter
// intentionally strips them for BLEACH, so changing the filter alone is not enough.
const source=JSON.parse(await fs.readFile(inside('catalog/invincible-sources.json'),'utf8'));
const lookup=new Map(source.sources.map(c=>[c.id,c]));
const dataPattern=/<script id="collection-data" type="application\/json">([\s\S]*?)<\/script>/;
const found=html.match(dataPattern);
if(!found)throw Error('Cannot find embedded collection data for identity enrichment');
const cards=JSON.parse(found[1]);
for(const card of cards){
  const id=card.id.replace(/--[a-z0-9-]+$/,'');
  const identity=lookup.get(id);
  if(!identity)throw Error('Unknown Invincible candidate in gallery: '+id);
  card.title=identity.title;card.aliases=identity.aliases;
}
html=html.replace(dataPattern,'<script id="collection-data" type="application/json">'+JSON.stringify(cards).replace(/</g,'\\u003c')+'</script>');
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
