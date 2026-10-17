import fs from 'node:fs/promises';
import path from 'node:path';
import {digest,findEntries} from './catalogue.mjs';

export async function openCardLibrary(folder) {
  const manifest=JSON.parse(await fs.readFile(path.join(folder,'manifest.json'),'utf8'));
  if(manifest.version!==1||!Array.isArray(manifest.cards))throw Error('Unsupported card library');
  const cards=manifest.cards.filter(c=>c.approved===true&&c.review?.status==='approved');
  return {manifest,find:(query,options)=>findEntries(cards,query,options),async image(query,options){
    const matches=findEntries(cards,query,options);
    if(matches.length!==1){const e=Error(matches.length?'Choose a form and finish':'No approved card matches');e.code=matches.length?'CARD_AMBIGUOUS':'CARD_NOT_FOUND';e.choices=matches.map(c=>({id:c.id,form:c.form,design:c.design}));throw e;}
    const card=matches[0];
    if(path.basename(card.png)!==card.png)throw Error('Unsafe card path');
    const buffer=await fs.readFile(path.join(folder,card.png));
    if(digest(buffer)!==card.pngSHA256)throw Error('Card file integrity check failed');
    return {buffer,mimetype:'image/png',filename:`NIGHT-${card.id}.png`,card,caption:`NIGHT · ${card.name}\n${card.formId==='featured'?card.design:card.form+' · '+card.design}`};
  }};
}
