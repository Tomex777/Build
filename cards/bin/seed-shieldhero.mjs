#!/usr/bin/env node
/**
 * NIGHT Shield Hero official figure acquisition.
 *
 * Downloads official, rights-reserved reference-only portraits into the local
 * ignored artwork folder. Pins native dimensions and SHA-256 in derived catalogue
 * and power records. Never marks an image artistically approved, publishes
 * it to the bot, or claims official power rankings.
 *
 * Existing source pins are immutable by default: if an upstream file changes,
 * the run fails rather than silently putting another pose on the same card.
 */
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside, atomic, digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';

const ROOT='https://shieldhero-anime.jp';
const sources=JSON.parse(await fs.readFile(inside('catalog/shieldhero-sources.json'),'utf8'));
const locked=JSON.parse(await fs.readFile(inside('catalog/shieldhero-source-lock.json'),'utf8').catch(error => {
  if(error.code==='ENOENT')return '{"version":1,"sources":{}}';
  throw error;
}));
if(sources.series!=='shieldhero'||sources.sources.length<1||locked.version!==1)
  throw Error('Invalid Shield Hero source manifest or lockfile');
const refresh=process.argv.includes('--refresh-pins');
const writeLock=process.argv.includes('--write-lock');
if(refresh&&!writeLock)throw Error('Refreshing artwork pins requires --write-lock');
const readFile=async name=>fs.readFile(inside(name),'utf8').then(JSON.parse).catch(e=>{
  if(e.code==='ENOENT')return null;
  throw e;
});
const previous=await readFile('catalog/shieldhero.json');
const seen=new Set(), numbers=new Set(), images=new Set(), checksums=new Set();
const catalogue={version:1,series:'shieldhero',name:'The Rising of the Shield Hero / NIGHT Collection',dimensions:[3000,4800],
  artworkPolicy:{distinctPerEdition:true},tierPolicy:{mode:'power-selects-design'},
  rightsNote:'Reference-only candidate art; Shield Hero anime images remain rights reserved; no independent public redistribution license.',
  cards:[],pendingForms:[]};
const power={version:1,series:'shieldhero',universe:'shieldhero',scope:'in-universe',
  scale:'night-universe-power-v1',status:'editorial-proposal',
  notes:'Form-specific, Shield Hero anime Seasons 3–4 only; power grades are independent NIGHT editorial proposals. Not a complete feat audit or official grading.',
  assessments:{}};

async function get(url){
  const target=new URL(url);
  if(target.origin!==ROOT||!/^\/assets\/img\/(?:3rd|4th)\/char\/detail\/[a-z0-9-]+\.png$/.test(target.pathname))
    throw Error('Unexpected artwork provider: '+url);
  const response=await fetch(url,{redirect:'follow',signal:AbortSignal.timeout(45000),
    headers:{'User-Agent':'NIGHT-CardResearch/1.0 (+source provenance verification)'}});
  if(!response.ok||new URL(response.url).origin!==ROOT)
    throw Error('Official artwork request failed for '+url+': '+response.status);
  const parts=[];let bytes=0;
  for await(const part of response.body){
    bytes+=part.length;
    if(bytes>15*1024*1024)throw Error('Official artwork exceeds 15MiB');
    parts.push(part);
  }
  return Buffer.concat(parts);
}
for(const item of sources.sources){
  if(!/^[a-z0-9-]+$/.test(item.id)||seen.has(item.id)||
      !Number.isInteger(item.number)||numbers.has(item.number)||
      images.has(item.source.image)||!/^https:\/\/shieldhero-anime\.jp\/assets\/img\/(?:3rd|4th)\/char\/detail\/[a-z0-9-]+\.png$/.test(item.source.image))
    throw Error('Duplicate or invalid source identity: '+item.id);
  seen.add(item.id);numbers.add(item.number);images.add(item.source.image);
  const file='artwork/shieldhero/'+item.id+'.'+new URL(item.source.image).pathname.split('.').at(-1);
  let bytes;
  try{bytes=await fs.readFile(inside(file));}
  catch(e){if(e.code!=='ENOENT')throw e;bytes=await get(item.source.image);await atomic(inside(file),bytes);}
  const sha=digest(bytes), native=await sharp(bytes,{limitInputPixels:30e6}).metadata();
  if(!['png','webp','jpeg'].includes(native.format)||native.width<500||native.height<900||!native.hasAlpha)
    throw Error('Artwork lacks adequate native dimensions or cutout transparency: '+item.id+' ('+native.width+'x'+native.height+')');
  const stats=await sharp(bytes).ensureAlpha().stats();
  if(stats.channels[3]?.min>=240)throw Error('Opaque source needs a manual, source-bound subject matte: '+item.id);
  if(checksums.has(sha))throw Error('Duplicate source bytes: '+item.id);
  checksums.add(sha);
  const pin=locked.sources[item.id];
  if(pin&&(!refresh)&&(pin.sha256!==sha||pin.width!==native.width||pin.height!==native.height))
    throw Error('Official image changed since approval/pinning: '+item.id);
  if(previous&&!refresh){
    const old=previous.cards.find(c=>c.id===item.id);
    if(old&&old.source.sha256!==sha)throw Error('Refuse silent image rebind: '+item.id);
  }
  const design=designForRank(item.rank.letter);
  const crop=[0,0,native.width,native.height];
  const artworkPlacement={[design]:{x:0,y:8,width:1000}};
  const aliases=[...new Set(item.aliases??[item.name,item.title,item.character])];
  catalogue.cards.push({id:item.id,number:item.number,character:item.character,
    name:item.name,title:item.title,form:item.form,formId:item.formId,aliases,
    designs:[design],status:'candidate',
    source:{...item.source,localPath:file,sha256:sha,width:native.width,height:native.height,artist:'Shield Hero anime production'},
    recipe:{crop,bottomPadding:0.18,edgeFade:0.006,bottomFade:0.025,focal:[0.50,0.32],artworkPlacement}});
  power.assessments[item.id]={
    scale:power.scale,status:'proposed',rank:item.rank.letter,
    formId:item.formId,artworkSHA256:sha,scope:item.rank.scope,
    basis:item.rank.basis,limits:item.rank.limits,references:[item.source.page,item.source.image],
    universe:'shieldhero',continuity:item.continuity
  };
  if(writeLock)locked.sources[item.id]={sha256:sha,width:native.width,height:native.height,image:item.source.image};
  process.stdout.write(item.id+' '+native.width+'x'+native.height+' '+sha+' '+item.rank.letter+' '+design+'\n');
}
if(locked.sources&&Object.keys(locked.sources).some(id=>!seen.has(id)))
  throw Error('Lockfile references removed character; verify manually');
await atomic(inside('catalog/shieldhero.json'),JSON.stringify(catalogue,null,2)+'\n');
await atomic(inside('catalog/power/shieldhero.json'),JSON.stringify(power,null,2)+'\n');
if(writeLock)await atomic(inside('catalog/shieldhero-source-lock.json'),JSON.stringify(locked,null,2)+'\n');
console.log('Curated '+catalogue.cards.length+' distinct Shield Hero card candidates; rights: reference-only; no visual approvals.');
