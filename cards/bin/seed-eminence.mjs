#!/usr/bin/env node
/** Source-authentic preview collection; official game standing artwork.
 * Not authorized for commercial redistribution, not visually approved.
 * Re-running checks pinned bytes and refuses silent artwork replacement.
 */
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';
const ROOT='https://www.shadow-garden-mog.jp';
const BASE=ROOT+'/wp-content/themes/officialsite/partials/page/dmg/assets/webp/sp/chara/img_';
const roster=JSON.parse(await fs.readFile(inside('catalog/eminence-roster.json'),'utf8'));
const specs=[
 ['shadow-base','shadow-adult'],['cid-student','sid-adult'],
 ['alpha-seven','alpha-adult'],['beta-combat','beta-adult'],
 ['delta-hunt','delta-adult'],
 ['epsilon-slime','epsilon-adult'],['zeta-secret','zeta-adult'],
 ['eta-equipment','eta-adult'],['beatrix-war-goddess','beatrix'],
 ['alexia-midgar','alexia'],['rose-empowered','rose'],
 ['iris-midgar','iris'],['claire-base','clare']
];
const locked=JSON.parse(await fs.readFile(inside('catalog/eminence-source-lock.json'),'utf8').catch(e=>{
 if(e.code==='ENOENT')return '{"version":1,"sources":{}}';throw e;
}));
const write=process.argv.includes('--write-lock'), cards=[], used=new Set();
const power={version:1,series:'eminence',universe:'eminence',scope:'in-universe',
 scale:'night-universe-power-v1',status:'editorial-proposal',
 notes:'Official MOG artwork; main-story identity/form grades remain editorial proposals, never game-exclusive Apocrypha scaling. 768x840 transparent reference art yields preview-only 3000x4800 exports, not print-grade native-res artwork.',
 assessments:{}};
for(const [id,name] of specs){
 const candidate=roster.cards.find(c=>c.id===id);
 if(!candidate||used.has(name)||!candidate.candidateRank)throw Error('Missing/duplicate source: '+id);
 used.add(name);
 const url=BASE+name+'.webp',localPath='artwork/eminence/'+id+'.webp';
 let bytes;
 try{bytes=await fs.readFile(inside(localPath));}
 catch(e){
   if(e.code!=='ENOENT')throw e;
   const res=await fetch(url,{signal:AbortSignal.timeout(30000)});
   if(!res.ok||new URL(res.url).origin!==ROOT)throw Error('Official image failed: '+res.status+' '+url);
   bytes=Buffer.from(await res.arrayBuffer());
   if(bytes.length<10000||bytes.length>12e6)throw Error('Unexpected size '+id);
   await atomic(inside(localPath),bytes);
 }
 const sha=digest(bytes),m=await sharp(bytes).metadata();
 if(m.format!=='webp'||m.width!==768||m.height!==840||!m.hasAlpha)throw Error('Official mobile cutout changed dimensions or lost transparency: '+id);
 const old=locked.sources[id];
 if(old && (old.sha256!==sha||old.width!==m.width||old.height!==m.height||old.image!==url))throw Error('Source changed: '+id);
 if(write)locked.sources[id]={sha256:sha,width:m.width,height:m.height,image:url};
 const design=designForRank(candidate.candidateRank);
 cards.push({id,number:candidate.number,character:candidate.character,name:candidate.name,title:candidate.name,
   form:candidate.form,formId:candidate.formId,aliases:[candidate.name,candidate.id],
   designs:[design],status:'candidate',source:{provider:'master-of-garden-official-sp',page:ROOT+'/dmg/',image:url,
     localPath,sha256:sha,width:m.width,height:m.height,artist:'Master of Garden production',
     credit:'Official Master of Garden game site',rights:'© Daisuke Aizawa / KADOKAWA / Shadow Garden / Master of Garden. All rights reserved.',
     usage:'reference-only; not licensed for redistribution'},
   recipe:{crop:[0,0,m.width,m.height],bottomPadding:.24,edgeFade:.004,bottomFade:.03,focal:[.5,.25],
     artworkPlacement:{[design]:{x:0,y:15,width:1000}}}
 });
 const p={scale:power.scale,status:'proposed',rank:candidate.candidateRank,
   formId:candidate.formId,artworkSHA256:sha,scope:candidate.form,basis:candidate.basis,
   limits:candidate.limits,references:[candidate.reference,url],universe:'eminence',
   continuity:candidate.continuity};
 if(['SSS','EX'].includes(candidate.candidateRank)){
    p.featReview={status:'verified',universe:'eminence',continuity:candidate.continuity,
      formId:candidate.formId,artworkSHA256:sha,
      locator:'Season 2 episode 12 — HIGHEST; Shadow vs Ragnarok; official story synopsis',
      claim:'Shadow directly faces the Fourth Demon Lord Ragnarok. EX denotes NIGHT editorial upper benchmark, not an official canonical numerical rating.',
      reference:'https://shadow-garden.jp/2nd/story/'};
 }
 power.assessments[id]=p;
 console.log('PINNED',id,sha.slice(0,16),m.width,m.height,candidate.candidateRank,design);
}
// Responsive composition corrections for compact official portrait art: ensure
// actual silhouettes cross the inner architectural border, not just effects.
const larger=new Set(['beta-combat','zeta-secret','rose-empowered']);
for(const card of cards)if(larger.has(card.id)){
 const design=card.designs[0];
 card.recipe.artworkPlacement[design]=card.id==='rose-empowered'
   ?{x:-400,y:-255,width:1800}
   :{x:-250,y:-65,width:1500};
}
const have=new Set(cards.map(c=>c.id));
const pending=roster.cards.filter(c=>!have.has(c.id)).map(c=>({
 id:c.id,character:c.character,name:c.name,form:c.formId,
 reason:c.editorialStatus==='needs-apocrypha-feat-audit'?'separate game continuity and verified art required':'distinct form-bound official image still missing'
}));
for(const c of roster.cards.filter(c=>!have.has(c.id))){
 power.assessments[c.id]={scale:power.scale,status:'pending',rank:null,universe:'eminence',
 continuity:c.continuity,formId:c.formId};
}
const catalogue={version:1,series:'eminence',name:'The Eminence in Shadow / NIGHT Collection',
 dimensions:[3000,4800],artworkPolicy:{distinctPerEdition:true},
 tierPolicy:{mode:'power-selects-design'},rightsNote:'All official reference artwork is rights-reserved, not licensed for commercial redistribution.',
 status:'candidate-only',cards,pendingForms:pending};
await atomic(inside('catalog/eminence.json'),JSON.stringify(catalogue,null,2)+'\n');
await atomic(inside('catalog/power/eminence.json'),JSON.stringify(power,null,2)+'\n');
if(write)await atomic(inside('catalog/eminence-source-lock.json'),JSON.stringify(locked,null,2)+'\n');
console.log('Ready',cards.length,'render candidates and',pending.length,'separately pending forms. No publication clearance.');
