#!/usr/bin/env node
/**
 * Bungo Stray Dogs preview uses EXACTLY the shared BLEACH/NIGHT collection
 * presentation and SVG card renderer. Franchise labels and evidence change,
 * never the global design system.
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import {inside,loadCatalogue} from '../src/catalogue.mjs';
import {acquireArtwork} from '../src/artwork.mjs';
import {cardSvg} from '../src/render.mjs';
import {collectionGallery,collectionOrder} from '../src/gallery.mjs';

const dir=inside('dist/bungo-preview');
const cat=await loadCatalogue('bungo-stray-dogs');
if(cat.cards.length===0)throw Error('No source-bound official Bungo art available');
await fs.mkdir(path.join(dir,'cards'),{recursive:true});
const cards=[],failed=[];
for(const card of cat.cards){
 const design=card.designs[0];
 try{
  const art=await acquireArtwork(card);
  const svg=await cardSvg(card,art,design);
  if(!svg.includes('data-frame-boundary')||!svg.includes('data-layer="power-rank"'))
   throw Error('The NIGHT shared card renderer lost its depth or grade layer');
  const image='cards/'+card.id+'.svg';
  await fs.writeFile(path.join(dir,image),svg);
  cards.push({id:card.id+'--'+design,name:card.name,title:card.title,form:card.form,formId:card.formId,
    number:card.number,design,power:card.power,source:card.source.page,image});
  console.log('POWER CARD',card.name,card.power.rank,card.form);
 }catch(error){failed.push({id:card.id,error:error.message});console.error('QUARANTINED',card.id,error.message);}
}
if(!cards.length)throw Error('No renderable Bungo cards');
if(failed.length)throw Error('Incomplete Bungo collection: '+JSON.stringify(failed));
cards.sort(collectionOrder);
const report={series:'bungo-stray-dogs',cards};
let html=collectionGallery(report,{imageFor:c=>c.image,thumbnailFor:c=>c.image});
if(!html.includes('NIGHT / BLEACH'))throw Error('Shared gallery layout changed; review its replacement points');
html=html.replaceAll('BLEACH','BUNGO STRAY DOGS')
 .replace('Find a character or form','Find a character or ability')
 .replace('Rank reasoning','Power assessment');
await fs.writeFile(path.join(dir,'index.html'),html);
await fs.writeFile(path.join(dir,'manifest.json'),JSON.stringify({
  series:'bungo-stray-dogs',status:'candidate',continuity:cat.cards[0]?.power?.continuity,
  renderer:'NIGHT shared collectionGallery() + cardSvg()',count:cards.length,cards,failed
},null,2)+'\n');
console.log('EXACT SHARED NIGHT GALLERY',cards.length,'cards; all ability labels and power rank reasoning are displayed');
