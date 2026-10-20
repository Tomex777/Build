#!/usr/bin/env node
/**
 * Bootstrap the Bungo Stray Dogs collection from *verified* official anime
 * cutouts, never from logo images, thumbnails, or guessing an image's owner.
 * Unlike speculative roster data, only rows in the approved source map become
 * candidate cards. A 3000px export does not invent 4K source detail.
 */
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside, atomic, digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';

const roster=JSON.parse(await fs.readFile(inside('catalog/bungo-stray-dogs-roster.json'),'utf8'));
const sources=JSON.parse(await fs.readFile(inside('catalog/bungo-stray-dogs-sources.json'),'utf8'));
const lockFile='catalog/bungo-stray-dogs-source-lock.json';
const locked=JSON.parse(await fs.readFile(inside(lockFile),'utf8').catch(e=>{
  if(e.code==='ENOENT')return '{"version":1,"sources":{}}';
  throw e;
}));
if(roster.series!=='bungo-stray-dogs'||sources.series!==roster.series||locked.version!==1)
  throw Error('Bungo catalogue/lock series mismatch');
const refresh=process.argv.includes('--refresh-pins');
const writeLock=process.argv.includes('--write-lock');
if(refresh&&!writeLock)throw Error('Explicit --write-lock needed to refresh source pins');
const list=new Map(roster.roster.map(r=>[r.id,r]));
const images=new Set(),hashes=new Set(),ids=new Set();
const rights='© Kafka Asagiri, Sango Harukawa / KADOKAWA / Bungo Stray Dogs Production Committee. All rights reserved.';
const cat={version:1,series:roster.series,name:'Bungo Stray Dogs / NIGHT Collection',dimensions:[3000,4800],
  artworkPolicy:{distinctPerEdition:true},tierPolicy:{mode:'power-selects-design'},
  rightsNote:'Official anime imagery remains rights reserved. Candidate/reference-only, not licensed for redistribution.',
  cards:[],pendingForms:[]};
const power={version:1,series:roster.series,universe:roster.series,scope:'in-universe',
  scale:'night-universe-power-v1',status:'editorial-proposal',
  notes:'BSD anime continuity; independent, unapproved power proposals. Not a completed feat audit.',
  assessments:{}};
const officialHosts=new Set(['bungosd.com','www.bungosd.com','bungo-stray-dogs.jp','www.bungo-stray-dogs.jp']);
async function imageBytes(url){
  const u=new URL(url);
  if(!officialHosts.has(u.hostname)||u.protocol!=='https:')throw Error('Not an official source host: '+url);
  const response=await fetch(url,{redirect:'follow',signal:AbortSignal.timeout(45000),
    headers:{'User-Agent':'NIGHT-CardResearch/1.0'}});
  if(!response.ok||!officialHosts.has(new URL(response.url).hostname))
    throw Error('Artwork download failed or redirected outside official source: '+response.status);
  const chunks=[];let n=0;
  for await(const chunk of response.body){
    n+=chunk.length;if(n>18*1024*1024)throw Error('Official artwork exceeds 18MiB');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}
for(const s of sources.sources){
  const r=list.get(s.id);
  if(!r||ids.has(s.id)||s.verifiedCharacter!==r.name||s.verifiedForm!==r.formId||
      !s.reviewedBy||!s.image||images.has(s.image))
    throw Error('Artwork source requires a distinct verified character and exact form: '+s.id);
  ids.add(s.id);images.add(s.image);
  const u=new URL(s.image),page=new URL(s.page);
  if(!officialHosts.has(u.hostname)||!officialHosts.has(page.hostname)||u.protocol!=='https:'||page.protocol!=='https:')
    throw Error('Official-page and image host verification failed: '+s.id);
  const extension=u.pathname.split('.').at(-1).toLowerCase();
  if(!['png','webp','jpg','jpeg'].includes(extension))throw Error('Unsupported source extension: '+s.id);
  const localPath='artwork/bungo-stray-dogs/'+s.id+'.'+extension;
  let bytes;
  try{bytes=await fs.readFile(inside(localPath));}
  catch(e){if(e.code!=='ENOENT')throw e;bytes=await imageBytes(s.image);await atomic(inside(localPath),bytes);}
  const meta=await sharp(bytes,{limitInputPixels:30e6}).metadata();
  if(!['png','jpeg','webp'].includes(meta.format)||meta.width<500||meta.height<900||!meta.hasAlpha)
    throw Error('Cutout requires RGBA and native size >=500x900: '+s.id+' ('+meta.width+'x'+meta.height+')');
  const stats=await sharp(bytes).ensureAlpha().stats();
  if(stats.channels[3]?.min>=240)throw Error('Opaque poster requires a reviewed matte instead: '+s.id);
  const sha=digest(bytes),prior=locked.sources[s.id];
  if(hashes.has(sha))throw Error('Artwork file reused by multiple distinct editions: '+s.id);
  hashes.add(sha);
  if(prior&&!refresh&&(prior.sha256!==sha||prior.width!==meta.width||prior.height!==meta.height||prior.image!==s.image))
    throw Error('A pinned original changed, inspect before --refresh-pins: '+s.id);
  if(writeLock)locked.sources[s.id]={sha256:sha,width:meta.width,height:meta.height,image:s.image};
  const design=designForRank(r.proposedRank);
  cat.cards.push({
    id:r.id,number:r.number,character:r.id,name:r.name,title:s.displayTitle??r.name,
    form:s.formLabel??'Official anime portrait',formId:r.formId,
    aliases:[r.name,r.japaneseName,r.id],status:'candidate',designs:[design],
    source:{provider:'bungo-official-anime',page:s.page,image:s.image,
      rights,usage:'reference-only',credit:'Official Bungo Stray Dogs anime website',
      artist:'Bungo Stray Dogs anime production',sha256:sha,width:meta.width,height:meta.height,localPath},
    recipe:{crop:[0,0,meta.width,meta.height],bottomPadding:.20,edgeFade:.015,
      bottomFade:.045,focal:[.5,.33],artworkPlacement:{[design]:{x:0,y:30,width:1000}}}
  });
  power.assessments[r.id]={scale:power.scale,status:'proposed',rank:r.proposedRank,
    formId:r.formId,artworkSHA256:sha,scope:r.name+' — '+(s.formLabel??'official anime portrait'),
    basis:r.basis,limits:r.limits,references:[s.page,s.image],universe:roster.series,
    continuity:roster.continuity};
  console.log('CANDIDATE',r.id,meta.width+'x'+meta.height,sha,design,r.proposedRank);
}
for(const r of roster.roster.filter(x=>!ids.has(x.id))){
  cat.pendingForms.push({id:r.id,character:r.id,name:r.name,form:r.formId,status:'pending-official-matched-cutout'});
  power.assessments[r.id]={scale:power.scale,status:'pending',rank:null,
    universe:roster.series,continuity:roster.continuity,formId:r.formId,
    artworkSHA256:null,scope:r.name,basis:r.basis,limits:r.limits,
    references:[roster.officialCharacterPage]};
}
for(const form of roster.pendingForms){
  if(power.assessments[form.id])throw Error('Pending form collides with source-backed edition');
  cat.pendingForms.push({id:form.id,character:form.character,name:form.name,form:form.formId,status:form.status});
  power.assessments[form.id]={scale:power.scale,status:'pending',rank:null,
    universe:roster.series,continuity:roster.continuity,formId:form.formId,
    artworkSHA256:null,scope:form.name,basis:'Separate character condition requiring a unique official visual.',
    limits:'Unacquired forms never inherit another costume or transformation.',
    references:[roster.officialCharacterPage]};
}
for(const id of Object.keys(locked.sources)) if(!ids.has(id))throw Error('Artwork source removed from manifest: '+id);
await atomic(inside('catalog/bungo-stray-dogs.json'),JSON.stringify(cat,null,2)+'\n');
await atomic(inside('catalog/power/bungo-stray-dogs.json'),JSON.stringify(power,null,2)+'\n');
if(writeLock)await atomic(inside(lockFile),JSON.stringify(locked,null,2)+'\n');
console.log(cat.cards.length+' verified source-backed BSD candidates; '+cat.pendingForms.length+' pending. No auto-approval.');
