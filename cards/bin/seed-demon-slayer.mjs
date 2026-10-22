#!/usr/bin/env node
/**
 * Download real Mitsuri original cutout from the official Demon Slayer game.
 * Pin native bytes, image identity, SHA-256 and dimensional evidence.
 * No generated art, inferred licenses, auto-approvals or live publication.
 */
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';

const manifest=JSON.parse(await fs.readFile(inside('catalog/demon-slayer-sources.json'),'utf8'));
const lockPath='catalog/demon-slayer-source-lock.json';
const locks=JSON.parse(await fs.readFile(inside(lockPath),'utf8').catch(error=>{
  if(error.code==='ENOENT')return '{"version":1,"sources":{}}';
  throw error;
}));
const writeLock=process.argv.includes('--write-lock');
const refresh=process.argv.includes('--refresh-pins');
if(refresh&&!writeLock)throw Error('--refresh-pins must be paired with --write-lock');
if(!writeLock&&!Object.keys(locks.sources).length)
  throw Error('First acquisition needs --write-lock and later human artwork review');
if(manifest.series!=='demon-slayer'||locks.version!==1||!manifest.sources?.length)
  throw Error('Invalid Demon Slayer source definitions');

async function original(url) {
  const address=new URL(url);
  if(address.origin!=='https://game.kimetsu.com'||
     !/^\/hinokami2\/assets\/img\/character\/main\/chara\d+\.png$/.test(address.pathname))
    throw Error('Mitsuri must be sourced from the verified official game character portrait');
  const response=await fetch(url,{redirect:'error',signal:AbortSignal.timeout(45000),
    headers:{'User-Agent':'NIGHT-CardProvenance/1.0'}});
  if(!response.ok||response.url!==url)
    throw Error('Official image request failed or redirected: '+response.status);
  const parts=[];let total=0;
  for await(const chunk of response.body) {
    total+=chunk.length;
    if(total>18*1024*1024)throw Error('Original image exceeds allowed bytes');
    parts.push(chunk);
  }
  return Buffer.concat(parts);
}

const catalogue={
  version:1,series:'demon-slayer',name:'Demon Slayer / NIGHT Collection',
  dimensions:[3000,4800],artworkPolicy:{distinctPerEdition:true},
  tierPolicy:{mode:'power-selects-design',status:'proposed'},
  rightsNote:'Official game art is reference-only; commercial and public redistribution need rights clearance.',
  cards:[],pendingForms:[]
};
const power={
  version:1,series:'demon-slayer',universe:'demon-slayer',
  scope:'in-universe',scale:'night-universe-power-v1',status:'editorial-proposal',
  notes:'Mitsuri form-specific Demon Slayer anime NIGHT proposal, not official numeric grading.',
  assessments:{}
};
const ids=new Set(),images=new Set(),numbers=new Set();
for(const item of manifest.sources) {
  if(!/^[a-z][a-z0-9-]+$/.test(item.id)||ids.has(item.id)||
     !Number.isInteger(item.number)||numbers.has(item.number)||images.has(item.source.image))
    throw Error('Duplicate or invalid source identity');
  ids.add(item.id);numbers.add(item.number);images.add(item.source.image);
  const localPath='artwork/demon-slayer/'+item.id+'.png';
  let bytes;
  try{bytes=await fs.readFile(inside(localPath));}
  catch(e){if(e.code!=='ENOENT')throw e;bytes=await original(item.source.image);}
  const sha256=digest(bytes);
  const meta=await sharp(bytes,{limitInputPixels:30e6,failOn:'error'}).metadata();
  if(meta.format!=='png'||meta.width<500||meta.height<900||!meta.hasAlpha)
    throw Error('Original image is not a sufficiently large transparent PNG; do not generate a replacement');
  const stats=await sharp(bytes).ensureAlpha().stats();
  if(stats.channels[3].min>=220||stats.channels[3].max<250)
    throw Error('Original image lacks a clear transparent subject silhouette');
  const pin=locks.sources[item.id];
  if(pin&&!refresh&&(pin.sha256!==sha256||pin.width!==meta.width||
     pin.height!==meta.height||pin.image!==item.source.image))
    throw Error('Official Mitsuri artwork has changed since pinning; needs human review');
  await atomic(inside(localPath),bytes);
  if(writeLock)locks.sources[item.id]={sha256,width:meta.width,height:meta.height,image:item.source.image};
  const design=designForRank(item.rank.letter),crop=[0,0,meta.width,meta.height];
  catalogue.cards.push({
    id:item.id,number:item.number,character:item.character,
    name:item.name,title:item.title,form:item.form,formId:item.formId,
    aliases:item.aliases,designs:[design],status:'candidate',
    source:{...item.source,localPath,sha256,width:meta.width,height:meta.height},
    recipe:{crop,bottomPadding:.20,edgeFade:.01,bottomFade:.035,
      focal:[.50,.30],artworkPlacement:{[design]:{x:-90,y:65,width:1180}}}
  });
  power.assessments[item.id]={
    scale:power.scale,status:'proposed',rank:item.rank.letter,universe:'demon-slayer',
    continuity:item.continuity,formId:item.formId,artworkSHA256:sha256,
    scope:item.rank.scope,basis:item.rank.basis,limits:item.rank.limits,
    references:[...item.rank.references,item.source.image]
  };
  process.stdout.write('SOURCE '+item.id+' '+meta.width+'x'+meta.height+
    ' sha256='+sha256+' rank='+item.rank.letter+' design='+design+'\n');
}
// Marked Mitsuri is not given the unmarked image or a made-up grade.
if(manifest.pendingForms?.some(c=>ids.has(c.id)))
  throw Error('Marked and unmarked forms must have distinct artwork identities');
if(!refresh&&Object.keys(locks.sources).some(id=>!ids.has(id)))
  throw Error('Old pinned source cannot be silently dropped');
await atomic(inside('catalog/demon-slayer.json'),JSON.stringify(catalogue,null,2)+'\n');
await atomic(inside('catalog/power/demon-slayer.json'),JSON.stringify(power,null,2)+'\n');
if(writeLock)await atomic(inside(lockPath),JSON.stringify(locks,null,2)+'\n');
console.log('Real-art Demon Slayer candidates prepared: '+catalogue.cards.length+
  '. No AI, no approval, no deployment.');
