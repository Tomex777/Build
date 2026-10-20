#!/usr/bin/env node
/**
 * Source-bound original-art seed for The Misfit of Demon King Academy.
 * Creates data for NIGHT's existing 3000×4800 shared renderer. Never invents
 * artwork, substitutes poses, approves art, or calls proposed tiers official.
 */
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside, atomic, digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';

const ROOT='https://maohgakuin.com';
const sources=JSON.parse(await fs.readFile(inside('catalog/maou-gakuin-sources.json'),'utf8'));
const lock=JSON.parse(await fs.readFile(inside('catalog/maou-gakuin-source-lock.json'),'utf8'));
if(sources.series!=='maou-gakuin'||sources.sources.length<1||lock.version!==1) throw Error('Invalid Maou Gakuin source input');
const writeLock=process.argv.includes('--write-lock');
const refresh=process.argv.includes('--refresh-pins');
if(refresh&&!writeLock)throw Error('A pin refresh requires --write-lock and visual review.');
const previous=await fs.readFile(inside('catalog/maou-gakuin.json'),'utf8').then(JSON.parse).catch(e=>{if(e.code==='ENOENT')return null;throw e;});
const catalogue={version:1,series:'maou-gakuin',name:'The Misfit of Demon King Academy / NIGHT Collection',dimensions:[3000,4800],
  artworkPolicy:{distinctPerEdition:true},tierPolicy:{mode:'power-selects-design'},
  rightsNote:'Original anime artwork is copyright protected; reference-only preview candidates.',cards:[],pendingForms:[]};
const power={version:1,series:'maou-gakuin',universe:'maou-gakuin',scope:'in-universe',
  scale:'night-universe-power-v1',status:'editorial-proposal',
  notes:'Anime-depicted forms only. These grades are editorial proposals; light-novel endgame editions remain separate.',assessments:{}};
const seenIds=new Set(), seenNums=new Set(), seenImages=new Set(), seenSHA=new Set();
async function getImage(url) {
  const target=new URL(url);
  if(target.origin!==ROOT||!(target.pathname.startsWith('/assets/img/character/img_') && target.pathname.endsWith('.png')))
    throw Error('Source URL is not a vetted official character cutout: '+url);
  const res=await fetch(url,{redirect:'follow',signal:AbortSignal.timeout(55000),headers:{'User-Agent':'NIGHT-CardResearch/1.0'}});
  if(!res.ok||new URL(res.url).origin!==ROOT)throw Error('Official image unavailable: '+url+' '+res.status);
  const pieces=[];let size=0;
  for await(const chunk of res.body){size+=chunk.length;if(size>12*1024*1024)throw Error('Artwork exceeds 12MB');pieces.push(chunk);}
  return Buffer.concat(pieces);
}
for(const item of sources.sources) {
  if(!/^[a-z0-9-]+$/.test(item.id)||seenIds.has(item.id)||!Number.isInteger(item.number)||seenNums.has(item.number)||seenImages.has(item.source.image))
    throw Error('Duplicate/invalid artwork identity: '+item.id);
  seenIds.add(item.id);seenNums.add(item.number);seenImages.add(item.source.image);
  // Production anime character pages publish native 720×880 cutouts. Keep
  // original bytes untouched in a separate cache, and explicitly label the
  // lossless-alpha Lanczos resampling that produces renderer-safe assets.
  // This improves antialiasing, NOT physical native detail. Do not claim the
  // resized crop is a native 4K illustration.
  const originalFile='artwork/maou-gakuin/original/'+item.id+'.png';
  const file='artwork/maou-gakuin/'+item.id+'.png';
  const original=await fs.readFile(inside(originalFile)).catch(async error=>{if(error.code!=='ENOENT')throw error;const downloaded=await getImage(item.source.image);await atomic(inside(originalFile),downloaded);return downloaded;});
  const originalSha256=digest(original), originalInfo=await sharp(original,{limitInputPixels:30e6}).metadata();
  if(originalInfo.format!=='png'||!originalInfo.hasAlpha||originalInfo.width<350||originalInfo.height<650)throw Error('No adequate transparent native illustration: '+item.id);
  const bytes=await sharp(original).resize({width:1440,height:1760,kernel:'lanczos3'}).png().toBuffer();
  const sha=digest(bytes), info=await sharp(bytes).metadata();
  await atomic(inside(file),bytes);
  const alpha=(await sharp(bytes).ensureAlpha().stats()).channels[3];
  if(alpha.min>=245)throw Error('Artwork has no subject cutout transparency: '+item.id);
  if(seenSHA.has(sha))throw Error('Duplicate original artwork: '+item.id);
  seenSHA.add(sha);
  const pinned=lock.sources[item.id];
  if(pinned&&!refresh&&(pinned.sha256!==sha||pinned.width!==info.width||pinned.height!==info.height||pinned.originalSha256!==originalSha256))
    throw Error('Official image changed; manual inspection needed: '+item.id);
  const old=previous?.cards.find(c=>c.id===item.id);
  if(old&&!refresh&&old.source.sha256!==sha)throw Error('Original art changed; refuse to rebind silently: '+item.id);
  const design=designForRank(item.rank.letter);
  // Keep authentic body alpha intact and let NIGHT's global renderer
  // implement foreground frame crossing and glass tier lettering.
  const crop=[0,0,info.width,info.height];
  const record={id:item.id,character:item.character,name:item.name,title:item.title,form:item.form,formId:item.formId,
    number:item.number,aliases:[...new Set(item.aliases)],designs:[design],status:'candidate',
    source:{...item.source,localPath:file,sha256:sha,width:info.width,height:info.height,originalSha256,originalWidth:originalInfo.width,originalHeight:originalInfo.height,processing:'Lanczos3 2x upscale of original official alpha cutout; no added character details'},
    recipe:{crop,bottomPadding:0.16,edgeFade:0.005,bottomFade:0.02,focal:[0.5,0.34],
      artworkPlacement:{[design]:{x:0,y:15,width:1000}}}};
  catalogue.cards.push(record);
  power.assessments[item.id]={scale:power.scale,status:'proposed',rank:item.rank.letter,formId:item.formId,
    artworkSHA256:sha,scope:item.rank.scope,basis:item.rank.basis,limits:item.rank.limits,
    references:[item.source.page,item.source.image,...item.rank.references],universe:'maou-gakuin',continuity:item.continuity};
  if(writeLock)lock.sources[item.id]={sha256:sha,width:info.width,height:info.height,originalSha256,originalWidth:originalInfo.width,originalHeight:originalInfo.height,image:item.source.image};
  console.log(item.id,info.width+'×'+info.height,item.rank.letter,design,sha);
}
if(Object.keys(lock.sources).some(k=>!seenIds.has(k)))throw Error('Stale source lock identity. Investigate.');
await atomic(inside('catalog/maou-gakuin.json'),JSON.stringify(catalogue,null,2)+'\n');
await atomic(inside('catalog/power/maou-gakuin.json'),JSON.stringify(power,null,2)+'\n');
if(writeLock)await atomic(inside('catalog/maou-gakuin-source-lock.json'),JSON.stringify(lock,null,2)+'\n');
console.log('13 source-bound card candidates; none artistically approved for commercial release.');
