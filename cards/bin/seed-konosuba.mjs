#!/usr/bin/env node
/**
 * NIGHT KonoSuba — source-verified character art intake.
 * Keep original official image bytes (reference-only, all rights reserved).
 * Distinct character/form editions use distinct source images.
 * Low-native-resolution or nontransparent candidates are left pending rather
 * than masquerading as high-quality 3000×4800 originals.
 */
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';
const manifest=JSON.parse(await fs.readFile(inside('catalog/konosuba-sources.json'),'utf8'));
const lockPath=inside('catalog/konosuba-source-lock.json');
const lock=JSON.parse(await fs.readFile(lockPath,'utf8').catch(e=>e.code==='ENOENT'?'{"version":1,"sources":{}}':Promise.reject(e)));
if(manifest.series!=='konosuba'||lock.version!==1)throw Error('Invalid KonoSuba manifests');
const allIDs=new Set(),urls=new Set(),hashes=new Set();
const data={version:1,series:'konosuba',name:'KonoSuba / NIGHT Collection',dimensions:[3000,4800],
  artworkPolicy:{distinctPerEdition:true},tierPolicy:{mode:'power-selects-design'},
  rightsNote:'Rights-reserved official anime images; limited reference-only candidate exports. Not licensed for sale.',
  status:'candidates-unapproved',cards:[],pendingForms:[],sourceIssues:[]};
const power={version:1,series:'konosuba',universe:'konosuba',scope:'in-universe',
  scale:'night-universe-power-v1',status:'editorial-proposal',
  notes:'KonoSuba light novel feats inform overall powers; artwork is the identified character and not proof of unseen form-specific gear. All tiers are NIGHT editorial hypotheses, never official numerical rankings.',
  assessments:{}};
for(const row of manifest.sources){
  if(allIDs.has(row.id)||urls.has(row.source.image))throw Error('Repeated form or artwork: '+row.id);
  allIDs.add(row.id);urls.add(row.source.image);
  const url=new URL(row.source.image);
  if(url.origin!=='https://konosuba.com'||!/^\/(3rd|bakuen)\/character\/img\/[a-z0-9-]+\.png$/.test(url.pathname))throw Error('Disallowed source URL '+url);
  let response=await fetch(url,{redirect:'error',signal:AbortSignal.timeout(30000),headers:{'User-Agent':'NIGHT-Art-Provenance/1.0'}});
  if(!response.ok) {data.sourceIssues.push({id:row.id,reason:'HTTP '+response.status});continue;}
  const bytes=Buffer.from(await response.arrayBuffer());
  if(bytes.length>12*1024*1024)throw Error('Source too large: '+row.id);
  const sha=digest(bytes);
  if(hashes.has(sha))throw Error('Duplicate original artwork bytes: '+row.id);
  hashes.add(sha);
  const meta=await sharp(bytes,{limitInputPixels:25e6}).metadata();
  const file='artwork/konosuba/'+row.id+'.png';
  const pin=lock.sources[row.id];
  if(pin&&(pin.sha256!==sha||pin.width!==meta.width||pin.height!==meta.height))throw Error('Official source changed (do not silently rebind): '+row.id);
  lock.sources[row.id]??={sha256:sha,width:meta.width,height:meta.height,image:row.source.image};
  const found={...row.source,artist:'KonoSuba animation staff',localPath:file,
    sha256:sha,width:meta.width,height:meta.height,format:meta.format};
  const valid=meta.format==='png'&&meta.hasAlpha&&meta.width>=500&&meta.height>=900;
  if(!valid){
    const reason='Needs a higher-native-resolution, transparent character cutout ('+meta.width+'×'+meta.height+', alpha='+!!meta.hasAlpha+')';
    data.sourceIssues.push({id:row.id,reason,source:row.source.image,sha256:sha});
    data.pendingForms.push({id:row.id,character:row.character,name:row.name,form:row.formId,reason,source:row.source.image});
    power.assessments[row.id]={scale:power.scale,status:'pending',rank:null,formId:row.formId,universe:'konosuba',continuity:row.continuity,scope:row.rank.scope,basis:row.rank.basis,limits:row.rank.limits,references:row.rank.references};
    continue;
  }
  await atomic(inside(file),bytes);
  const imageStats=await sharp(bytes).ensureAlpha().stats();
  if(imageStats.channels[3].min>200){data.sourceIssues.push({id:row.id,reason:'Opaque appearance: source needs review/matte'});continue;}
  const design=designForRank(row.rank.letter);
  const recipe={crop:[0,0,meta.width,meta.height],bottomPadding:.23,edgeFade:.006,bottomFade:.035,
    focal:[.5,.35],artworkPlacement:{[design]:{x:0,y:5,width:1000}}};
  data.cards.push({id:row.id,number:row.number,character:row.character,name:row.name,title:row.title,
    form:row.form,formId:row.formId,aliases:[row.name,row.character,row.form],designs:[design],status:'candidate',
    source:found,recipe});
  power.assessments[row.id]={scale:power.scale,status:'proposed',rank:row.rank.letter,
    formId:row.formId,artworkSHA256:sha,universe:'konosuba',continuity:row.continuity,
    scope:row.rank.scope,basis:row.rank.basis,limits:row.rank.limits,
    references:row.rank.references,
    ...(row.feat?{featReview:{...row.feat,universe:'konosuba',continuity:row.continuity,formId:row.formId,artworkSHA256:sha}}:{})};
  console.log('CANDIDATE '+row.id+' '+meta.width+'x'+meta.height+' '+row.rank.letter+' '+design);
}
if(!data.cards.length)throw Error('No suitable full-body official cutouts; refusing fake card catalogue');
await atomic(inside('catalog/konosuba.json'),JSON.stringify(data,null,2)+'\n');
await atomic(inside('catalog/power/konosuba.json'),JSON.stringify(power,null,2)+'\n');
await atomic(lockPath,JSON.stringify(lock,null,2)+'\n');
await atomic(inside('catalog/konosuba-artwork-audit.json'),JSON.stringify({sourceCount:manifest.sources.length,
 accepted:data.cards.length,pending:data.pendingForms.length,sourceIssues:data.sourceIssues,
 rights:manifest.rights,notes:'Does not establish that a card is rendered or visually reviewed.'},null,2)+'\n');
console.log('Verified '+data.cards.length+' distinct form-art candidates; pending '+data.pendingForms.length+'. NO visual approvals.');
