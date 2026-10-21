#!/usr/bin/env node
/**
 * Official Invincible VS source-art acquisition for NIGHT's EXISTING renderer.
 * Power grades are in-universe editorial proposals (not the game's move balance).
 * This tool never substitutes an unrelated pose, fakes a form, or approves art.
 */
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';

const sourceList=JSON.parse(await fs.readFile(inside('catalog/invincible-sources.json'),'utf8'));
const lock=JSON.parse(await fs.readFile(inside('catalog/invincible-source-lock.json'),'utf8'));
const shouldWrite=process.argv.includes('--write-lock');
const refresh=process.argv.includes('--refresh-pins');
if(refresh&&!shouldWrite)throw Error('Refreshing pins requires --write-lock and follow-up human visual review');
if(sourceList.series!=='invincible'||sourceList.sources.length<1||lock.version!==1)throw Error('Bad Invincible inputs');
const old=await fs.readFile(inside('catalog/invincible.json'),'utf8').then(JSON.parse).catch(e=>e.code==='ENOENT'?null:Promise.reject(e));
const cards=[],assessments={},identity=new Set(),urls=new Set(),artSHAs=new Set();
async function fetchOfficial(url) {
  const target=new URL(url);
  if(target.origin!=='https://invinciblevs.com'||!/^\\/wp-content\\/uploads\\/20\\d\\d\\/\\d\\d\\/[A-Za-z0-9_.-]+\\.(?:webp|png)$/.test(target.pathname))throw Error('Unapproved host or image path '+url);
  const res=await fetch(url,{signal:AbortSignal.timeout(45000),headers:{'User-Agent':'Mozilla/5.0 (NIGHT collectible artwork research; rights reserved)','Accept':'image/webp,image/png,*/*'}});
  if(!res.ok||new URL(res.url).origin!==target.origin)throw Error('Official source inaccessible '+url+' HTTP '+res.status);
  const chunks=[];let length=0;
  for await(const chunk of res.body){length+=chunk.length;if(length>20*1024*1024)throw Error('Source exceeds 20MB '+url);chunks.push(chunk);}
  return Buffer.concat(chunks);
}
for(const item of sourceList.sources){
  if(!/^[a-z0-9-]+$/.test(item.id)||identity.has(item.id)||urls.has(item.source.image))throw Error('Duplicate source identity '+item.id);
  identity.add(item.id);urls.add(item.source.image);
  const originalFile='artwork/invincible/original/'+item.id+(/\\.png$/.test(item.source.image)?'.png':'.webp');
  const file='artwork/invincible/'+item.id+'.png';
  let original;
  try{original=await fs.readFile(inside(originalFile));}
  catch(e){if(e.code!=='ENOENT')throw e; original=await fetchOfficial(item.source.image); await atomic(inside(originalFile),original);}
  const m=await sharp(original,{limitInputPixels:30e6}).metadata();
  if(!['webp','png'].includes(m.format)||m.width<450||m.height<800||!m.hasAlpha)throw Error('Insufficient official alpha cutout: '+item.id+' '+JSON.stringify({format:m.format,width:m.width,height:m.height,alpha:m.hasAlpha}));
  const originalSha256=digest(original);
  const processed=await sharp(original).resize({height:1800,kernel:'lanczos3'}).png().toBuffer();
  const info=await sharp(processed).metadata(),sha=digest(processed);
  if(artSHAs.has(sha))throw Error('Same art used twice: '+item.id);
  artSHAs.add(sha);
  const pinned=lock.sources[item.id];
  if(pinned&&!refresh&&(pinned.sha256!==sha||pinned.originalSha256!==originalSha256||pinned.width!==info.width||pinned.height!==info.height))throw Error('Official art changed since last lock '+item.id);
  const previous=old?.cards?.find(c=>c.id===item.id);
  if(previous&&!refresh&&previous.source.sha256!==sha)throw Error('Source changed; refuse silently re-binding '+item.id);
  await atomic(inside(file),processed);
  const design=designForRank(item.rank.letter);
  cards.push({id:item.id,character:item.character,name:item.name,title:item.title,form:item.form,formId:item.formId,
    number:item.number,aliases:[...new Set(item.aliases)],designs:[design],status:'candidate',
    source:{...item.source,localPath:file,sha256:sha,width:info.width,height:info.height,originalSha256,originalWidth:m.width,originalHeight:m.height,processing:'Aspect-preserving Lanczos3 resize from official alpha portrait to 1800px height; native detail unchanged'},
    recipe:{crop:[0,0,info.width,info.height],bottomPadding:0.16,edgeFade:0.005,bottomFade:0.015,focal:[0.5,0.33],
      artworkPlacement:{[design]:item.pose}}});
  const power={scale:'night-universe-power-v1',status:'proposed',rank:item.rank.letter,formId:item.formId,
    artworkSHA256:sha,scope:item.rank.scope,basis:item.rank.basis,limits:item.rank.limits,
    references:[item.source.page,item.source.image,...item.rank.references],universe:'invincible',continuity:item.continuity};
  if(item.id==='battle-beast-armed')power.featReview={
    status:'verified',universe:'invincible',continuity:item.continuity,formId:item.formId,artworkSHA256:sha,
    locator:'Invincible (comic) #115–120, Battle Beast versus Grand Regent Thragg',
    claim:'Battle Beast sustains a brutal duel with Thragg, who ultimately kills him.',
    reference:'https://comicbook.com/comics/list/the-10-most-powerful-members-of-the-invincible-universe-ranked/'};
  assessments[item.id]=power;
  if(shouldWrite)lock.sources[item.id]={sha256:sha,width:info.width,height:info.height,originalSha256,originalWidth:m.width,originalHeight:m.height,image:item.source.image};
  console.log(item.id,info.width+'x'+info.height,item.rank.letter,design,sha);
}
if(Object.keys(lock.sources).some(id=>!identity.has(id)))throw Error('Stale lock entry not in source list');
const catalog={version:1,series:'invincible',name:'Invincible / NIGHT Collection',dimensions:[3000,4800],
 artworkPolicy:{distinctPerEdition:true},tierPolicy:{mode:'power-selects-design'},
 rightsNote:'Official Invincible VS assets copyright Skybound. No public or commercial reuse authorized.',cards,
 pendingForms:[]};
const power={version:1,series:'invincible',universe:'invincible',scope:'in-universe',scale:'night-universe-power-v1',
 status:'editorial-proposal',notes:'Main comic character baselines graded separately from the game-original Ella Mental. VS concept art does not establish a new costume or peak form.',assessments};
await atomic(inside('catalog/invincible.json'),JSON.stringify(catalog,null,2)+'\\n');
await atomic(inside('catalog/power/invincible.json'),JSON.stringify(power,null,2)+'\\n');
if(shouldWrite)await atomic(inside('catalog/invincible-source-lock.json'),JSON.stringify(lock,null,2)+'\\n');
console.log('Prepared '+cards.length+' genuine illustrated candidates. Pending artwork/forms: '+sourceList.pending.length+'. No visual approvals granted.');
