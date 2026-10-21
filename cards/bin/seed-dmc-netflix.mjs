#!/usr/bin/env node
// NIGHT collectible render preparation. Only Netflix TV continuity, actual
// source illustrations and real alpha silhouettes. No invented artwork.
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';

const sources=JSON.parse(await fs.readFile(inside('catalog/dmc-netflix-sources.json'),'utf8'));
const roster=JSON.parse(await fs.readFile(inside('catalog/dmc-netflix-roster.json'),'utf8'));
const lock=JSON.parse(await fs.readFile(inside('catalog/dmc-netflix-source-lock.json'),'utf8'));
if (sources.series!=='dmc-netflix'||roster.series!==sources.series) throw Error('Netflix data mismatch');
const refs=new Set(), shaSet=new Set(), imageSet=new Set();
const catalogue={version:1,series:'dmc-netflix',name:'Devil May Cry (Netflix) / NIGHT',dimensions:[3000,4800],artworkPolicy:{distinctPerEdition:true},tierPolicy:{mode:'power-selects-design'},rightsNote:sources.rights,cards:[],pendingForms:[]};
const power={version:1,series:'dmc-netflix',universe:'dmc-netflix',scope:'in-universe',scale:'night-universe-power-v1',status:'editorial-proposal',notes:'Netflix 2025–26 S1–S2 ONLY, independent from Capcom game canon, 2007 anime and unfilmed plot; editorial—not official—ranks.',assessments:{}};
const status={version:1,series:'dmc-netflix',successful:[],pending:[]};

async function get(url) {
  const u=new URL(url);
  if (u.protocol!=='https:' || !['static.wikia.nocookie.net','devilmaycry.fandom.com','vsbattles.fandom.com'].includes(u.hostname)) throw Error('Image host not vetted');
  const r=await fetch(url,{redirect:'follow',headers:{'User-Agent':'Mozilla/5.0 (NIGHT private source verification)'},signal:AbortSignal.timeout(45000)});
  if(!r.ok||!['static.wikia.nocookie.net','devilmaycry.fandom.com','vsbattles.fandom.com'].includes(new URL(r.url).hostname))throw Error('Image host/status rejected '+r.status);
  const chunks=[];let len=0;
  for await(const chunk of r.body){len+=chunk.length;if(len>16e6)throw Error('Image exceeds 16MB');chunks.push(chunk);}
  return Buffer.concat(chunks);
}
function whiteBg(r,g,b){return r>=238&&g>=238&&b>=238&&Math.max(r,g,b)-Math.min(r,g,b)<22;}
async function isolateSubject(input,id) {
  const meta=await sharp(input,{limitInputPixels:20e6}).metadata();
  if(!['png','jpeg','webp'].includes(meta.format)||meta.width<420||meta.height<760)throw Error('Source is too small or not a full portrait ('+meta.width+'x'+meta.height+')');
  const {data,info}=await sharp(input).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const w=info.width,h=info.height,n=w*h;
  let transparent=0;for(let i=3;i<data.length;i+=4)if(data[i]<200)transparent++;
  if(transparent/n < .02){
    // Only flood-fill pale near-white pixels CONNECTED TO THE CANVAS EDGE.
    // Never perform a whole-image color key that deletes Dante's white hair.
    const visited=new Uint8Array(n),queue=new Uint32Array(n);
    let first=0,last=0;
    const visit=p=>{if(visited[p])return;const o=p*4;if(!whiteBg(data[o],data[o+1],data[o+2]))return;visited[p]=1;queue[last++]=p;};
    for(let x=0;x<w;x++){visit(x);visit((h-1)*w+x)}
    for(let y=0;y<h;y++){visit(y*w);visit(y*w+w-1)}
    if(last/n<.02)throw Error('Not a white-backed silhouette; manual matte required');
    while(first<last){
      const p=queue[first++],x=p%w,y=(p/w)|0;
      if(x)visit(p-1);if(x<w-1)visit(p+1);if(y)visit(p-w);if(y<h-1)visit(p+w);
    }
    for(let p=0;p<n;p++){
      if(visited[p])data[p*4+3]=0;
      else{
        const x=p%w,y=(p/w)|0;
        const border=(x>0&&visited[p-1])||(x<w-1&&visited[p+1])||(y>0&&visited[p-w])||(y<h-1&&visited[p+w]);
        if(border&&whiteBg(data[p*4],data[p*4+1],data[p*4+2]))data[p*4+3]=Math.min(data[p*4+3],140);
      }
    }
  }
  let subject=0,clear=0;
  for(let p=0;p<n;p++){if(data[p*4+3]>75)subject++;if(data[p*4+3]<50)clear++}
  if(subject/n<.13||subject/n>.91||clear/n<.05)throw Error('Opaque background or silhouette too small; manual cutout needed');
  // Resampling makes the source large enough to render without adding detail.
  const processed=await sharp(data,{raw:info}).resize({height:Math.max(1750,h),kernel:'lanczos3'}).png().toBuffer();
  const outMeta=await sharp(processed).metadata();
  if(outMeta.width<500||outMeta.height<900)throw Error('Derived portrait unsuitable');
  return {processed,meta,outMeta,method:transparent/n>=.02?'Retained original transparent illustration; height resample only':'Connected-edge pale background separation; human visual-edge QA required'};
}
for(const item of sources.assets) {
  if(refs.has(item.id)||imageSet.has(item.url))throw Error('Repeated source/identity '+item.id);
  refs.add(item.id);imageSet.add(item.url);
  const slot=roster.editions.find(c=>c.id===item.id);
  if(!slot||slot.rank!==item.rank||slot.form!==item.form)throw Error('Roster/source/form conflict: '+item.id);
  try {
    const originalFile='artwork/dmc-netflix/original/'+item.id+'.png';
    const original=await fs.readFile(inside(originalFile)).catch(async e=>{if(e.code!=='ENOENT')throw e;const b=await get(item.url);await atomic(inside(originalFile),b);return b;});
    const srcSha=digest(original);
    const {processed,meta,outMeta,method}=await isolateSubject(original,item.id);
    const newSha=digest(processed);
    if(shaSet.has(newSha))throw Error('Duplicate posed character artwork');
    shaSet.add(newSha);
    if(lock.sources[item.id]&&(
      lock.sources[item.id].sha256!==newSha||lock.sources[item.id].originalSha256!==srcSha
    ))throw Error('Original asset changed; requires manual re-review');
    await atomic(inside('artwork/dmc-netflix/'+item.id+'.png'),processed);
    const design=designForRank(item.rank);
    catalogue.cards.push({id:item.id,number:item.number,character:item.character,name:item.name,title:item.title,form:item.form,formId:item.formId,aliases:[item.name,item.title,item.character],designs:[design],status:'candidate',
      source:{provider:'netflix-animation-reference',page:item.page,image:item.url,rights:sources.rights,usage:'private-reference-only',credit:'Netflix/Capcom/Studio Mir visual, mirrored by a fan-wiki for source checking',artist:'Studio Mir / Netflix',localPath:'artwork/dmc-netflix/'+item.id+'.png',sha256:newSha,width:outMeta.width,height:outMeta.height,originalSha256:srcSha,originalWidth:meta.width,originalHeight:meta.height,processing:method},
      recipe:{crop:[0,0,outMeta.width,outMeta.height],bottomPadding:.13,edgeFade:.002,bottomFade:.008,focal:[.5,.37],artworkPlacement:{[design]:{x:-130,y:-125,width:1260}}}});
    power.assessments[item.id]={scale:power.scale,status:'proposed',rank:item.rank,universe:'dmc-netflix',continuity:item.continuity,formId:item.formId,artworkSHA256:newSha,scope:item.name+' / '+item.form,basis:slot.basis,limits:slot.limits,references:slot.references};
    lock.sources[item.id]={sha256:newSha,originalSha256:srcSha,width:outMeta.width,height:outMeta.height,originalWidth:meta.width,originalHeight:meta.height,image:item.url};
    status.successful.push(item.id);
    console.log('SOURCE-BOUND',item.id,meta.width+'×'+meta.height,'→',outMeta.width+'×'+outMeta.height,design);
  }catch(err){status.pending.push({id:item.id,reason:err.message});console.warn('ARTWORK PENDING',item.id,err.message);}
}
catalogue.pendingForms=roster.editions.filter(c=>!catalogue.cards.some(b=>b.id===c.id)).map(c=>({id:c.id,character:c.character,name:c.name,form:c.form,continuity:'dmc-netflix-'+c.season,status:'artwork-pending'}));
for(const c of catalogue.pendingForms) power.assessments[c.id]={scale:power.scale,status:'pending',rank:null,universe:'dmc-netflix',continuity:c.continuity,formId:c.form,artworkSHA256:null,scope:c.name+' / '+c.form,basis:roster.editions.find(e=>e.id===c.id).basis,limits:'No matching verified Netflix artwork. Proposed tier in roster only.',references:roster.editions.find(e=>e.id===c.id).references};
await atomic(inside('catalog/dmc-netflix.json'),JSON.stringify(catalogue,null,2)+'\n');
await atomic(inside('catalog/power/dmc-netflix.json'),JSON.stringify(power,null,2)+'\n');
await atomic(inside('catalog/dmc-netflix-source-lock.json'),JSON.stringify(lock,null,2)+'\n');
await atomic(inside('dist/dmc-netflix-source-status.json'),JSON.stringify(status,null,2)+'\n');
console.log(status.successful.length+' processed Netflix-cutout candidates and '+catalogue.pendingForms.length+' pending, NO card publication/approval.');
if(!status.successful.length)process.exitCode=1;
