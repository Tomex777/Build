#!/usr/bin/env node
// NIGHT Naruto official-cutout acquisition. No character generation, mock-card
// rendering, automatic visual approval, or modification of the running bot.
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';
const ROOT='https://naruto-game.bngames.net';
const sources=JSON.parse(await fs.readFile(inside('catalog/naruto-sources.json'),'utf8'));
const locked=JSON.parse(await fs.readFile(inside('catalog/naruto-source-lock.json'),'utf8').catch(e=>{
  if(e.code==='ENOENT')return '{"version":1,"sources":{}}';throw e;
}));
if(sources.series!=='naruto'||!sources.sources?.length||locked.version!==1)throw Error('Invalid Naruto source manifest');
const refresh=process.argv.includes('--refresh-pins'),writeLock=process.argv.includes('--write-lock');
if(refresh&&!writeLock)throw Error('Refreshing source pins requires --write-lock');
const readJson=async file=>fs.readFile(inside(file),'utf8').then(JSON.parse).catch(e=>{if(e.code==='ENOENT')return null;throw e});
const previous=await readJson('catalog/naruto.json');
const ids=new Set(),numbers=new Set(),urls=new Set(),hashes=new Set();
const catalogue={version:1,series:'naruto',name:'Naruto / NIGHT Collection',dimensions:[3000,4800],
 artworkPolicy:{distinctPerEdition:true},tierPolicy:{mode:'power-selects-design'},
 rightsNote:'Rights-reserved original official game art. Candidate review only; not publicly licensed.',
 powerPolicy:'Draft local assessments awaiting cross-universe consistency review',cards:[],pendingForms:[]};
const power={version:1,series:'naruto',universe:'naruto',scope:'in-universe',
 scale:'night-universe-power-v1',status:'editorial-proposal',
 notes:'Tentative form-specific ranks. Cross-universe calibration with BLEACH/Solo Leveling is required before approval.',
 assessments:{}};
async function get(url) {
 const expected=new URL(url);
 if(expected.origin!==ROOT||!/^\\/images\\/character\\/\\d{2}_\\d{2}\\/img_character\\.png$/.test(expected.pathname))
  throw Error('Source is not an approved official Naruto game cutout: '+url);
 const response=await fetch(url,{redirect:'follow',signal:AbortSignal.timeout(45000),
  headers:{'User-Agent':'NIGHT-Naruto-Art-Provenance/1.0'}});
 if(!response.ok||new URL(response.url).origin!==ROOT)throw Error('Source unavailable: '+url+' ['+response.status+']');
 let size=0;const chunks=[];
 for await(const chunk of response.body){size+=chunk.length;if(size>14*1024*1024)throw Error('Oversized official artwork');chunks.push(chunk)}
 return Buffer.concat(chunks);
}
for(const item of sources.sources){
 const {id,number,source}=item;
 if(!/^[a-z0-9-]+$/.test(id)||ids.has(id)||!Number.isInteger(number)||numbers.has(number)||
    urls.has(source.image)||source.provider!=='bandai-storm4-official')throw Error('Duplicate or invalid Naruto identity: '+id);
 ids.add(id);numbers.add(number);urls.add(source.image);
 const code=new URL(source.image).pathname.match(/\\/(\\d{2}_\\d{2})\\/img_character\\.png$/)?.[1];
 if(!code||source.page!==ROOT+'/character/'+code+'.html')throw Error('Source/character page mismatch: '+id);
 const localPath='artwork/naruto/'+id+'.png';
 let bytes;
 try{bytes=await fs.readFile(inside(localPath))}catch(e){if(e.code!=='ENOENT')throw e;bytes=await get(source.image);await atomic(inside(localPath),bytes)}
 const sha256=digest(bytes),native=await sharp(bytes,{limitInputPixels:30e6}).metadata();
 if(native.format!=='png'||native.width<350||native.height<750||!native.hasAlpha)
   throw Error('Image lacks sufficient real transparent character art: '+id+' '+native.width+'x'+native.height);
 const alpha=(await sharp(bytes).ensureAlpha().stats()).channels[3];
 if(alpha.min>20||alpha.max<230)throw Error('Character image lacks usable real alpha silhouette: '+id);
 if(hashes.has(sha256))throw Error('Duplicate character or form art: '+id);
 hashes.add(sha256);
 const pin=locked.sources[id];
 if(pin&&!refresh&&(pin.sha256!==sha256||pin.width!==native.width||pin.height!==native.height))
   throw Error('Source changed without visual review: '+id);
 const old=previous?.cards.find(c=>c.id===id);
 if(old&&!refresh&&old.source.sha256!==sha256)throw Error('Silent artwork rebind refused: '+id);
 const design=designForRank(item.rank.letter);
 catalogue.cards.push({id,number,character:item.character,name:item.name,title:item.title,
  form:item.form,formId:item.formId,aliases:[...new Set(item.aliases??[])],designs:[design],status:'candidate',
  source:{...source,localPath,sha256,width:native.width,height:native.height,artist:'Official Naruto: Ultimate Ninja STORM 4 game art'},
  recipe:{crop:[0,0,native.width,native.height],bottomPadding:0.20,edgeFade:0.006,bottomFade:0.028,
    focal:[0.50,0.32],artworkPlacement:{[design]:{x:0,y:28,width:1000}}}});
 power.assessments[id]={scale:power.scale,status:'proposed',rank:item.rank.letter,formId:item.formId,
  artworkSHA256:sha256,scope:item.rank.scope,basis:item.rank.basis,limits:item.rank.limits,
  references:[source.page,source.image],universe:'naruto',continuity:item.continuity};
 if(writeLock)locked.sources[id]={sha256,width:native.width,height:native.height,image:source.image};
 process.stdout.write(id+' '+native.width+'x'+native.height+' '+sha256+' '+item.rank.letter+' '+design+'\\n');
}
if(Object.keys(locked.sources).some(id=>!ids.has(id)))throw Error('Removed or renamed locked source must be reviewed');
for(const item of sources.pendingForms??[]){
 catalogue.pendingForms.push({id:item.id,character:item.character,form:item.formId,status:'pending-art',reason:item.reason});
 power.assessments[item.id]={scale:power.scale,status:'pending',rank:null,formId:item.formId,artworkSHA256:null,
  scope:item.character+' / '+item.formId,basis:'Pending verified original form-specific illustration and feats.',
  limits:'Do not assign another form image or grade.',references:[sources.sourcePage],
  universe:'naruto',continuity:'naruto-and-boruto'};
}
await atomic(inside('catalog/naruto.json'),JSON.stringify(catalogue,null,2)+'\\n');
await atomic(inside('catalog/power/naruto.json'),JSON.stringify(power,null,2)+'\\n');
if(writeLock)await atomic(inside('catalog/naruto-source-lock.json'),JSON.stringify(locked,null,2)+'\\n');
console.log('Naruto: '+catalogue.cards.length+' distinct candidate cutouts, '+catalogue.pendingForms.length+' pending. None visually approved.');
