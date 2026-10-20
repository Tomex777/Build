#!/usr/bin/env node
/**
 * Reproducibly acquire real (NOT generated) ONE PIECE character artwork.
 * The official Film Red sheets show two outfits; each selected outfit is
 * isolated by cropping its ORIGINAL pixels, then trimming transparent space.
 * No generative art, color reconstruction, upscaling or pose substitutions.
 */
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';
import {designForRank} from '../src/tiers.mjs';

const manifest=JSON.parse(await fs.readFile(inside('catalog/one-piece-sources.json'),'utf8'));
const pinsFile=inside('catalog/one-piece-source-lock.json');
let lock;
try{lock=JSON.parse(await fs.readFile(pinsFile,'utf8'))}
catch(e){if(e.code!=='ENOENT')throw e;lock={version:1,sources:{}}}
const writeLock=process.argv.includes('--write-lock');
const refresh=process.argv.includes('--refresh-pins');
if(refresh&&!writeLock)throw Error('Refreshing existing source pins needs --write-lock');
if(manifest.series!=='one-piece'||!manifest.sources?.length||lock.version!==1)
  throw Error('Invalid ONE PIECE source list or source-lock schema');
const CATALOG={version:1,series:'one-piece',name:'ONE PIECE / NIGHT Collection',
  dimensions:[3000,4800],artworkPolicy:{distinctPerEdition:true},
  tierPolicy:{mode:'power-selects-design'},
  notes:'Reference-only isolated official artwork. Candidate visual reviews required.',cards:[],pendingForms:[]};
const POWER={version:1,series:'one-piece',universe:'one-piece',scope:'in-universe',
  scale:'night-universe-power-v1',status:'editorial-proposal',
  notes:'Exact Film Red illustrated outfits. No Gear 5, Asura, Ifrit Jambe, Demonio Fleur or other transformation inferred from clothing.',
  assessments:{}};
const seen=new Set(),numbers=new Set(),sourceURLs=new Set(),hashes=new Set();

const get=async(url)=>{
  const p=new URL(url);
  if(p.protocol!=='https:'||p.hostname!=='www.onepiece-film.jp'||!p.pathname.endsWith('.png')||!p.pathname.includes('/character/'))
    throw Error('Not an allowlisted official Film Red artwork URL: '+url);
  const r=await fetch(url,{redirect:'error',signal:AbortSignal.timeout(55000),headers:{'User-Agent':'Mozilla/5.0 (compatible; NIGHTCardSourceReview/1.0)','Accept':'image/png,image/*;q=0.8'}});
  if(!r.ok)throw Error('Official art HTTP '+r.status+': '+url);
  let size=0;const parts=[];
  for await(const part of r.body){size+=part.length;if(size>16*1024*1024)throw Error('Art exceeds 16MiB');parts.push(part);}
  return Buffer.concat(parts);
};
const rawDir='.cache/one-piece-originals/';
for(const item of manifest.sources){
  if(seen.has(item.id)||numbers.has(item.number)||sourceURLs.has(item.original.image))
    throw Error('Duplicate card ID, card number or original art URL: '+item.id);
  seen.add(item.id);numbers.add(item.number);sourceURLs.add(item.original.image);
  const rawFile=inside(rawDir+item.id+'.png');
  let bytes;
  try{bytes=await fs.readFile(rawFile)}
  catch(e){if(e.code!=='ENOENT')throw e;bytes=await get(item.original.image);await atomic(rawFile,bytes)}
  const sourceSHA256=digest(bytes);
  const m=await sharp(bytes,{limitInputPixels:32e6}).metadata();
  if(m.format!=='png'||!m.hasAlpha||m.width<480||m.height<580)
    throw Error('Original source is not a usable transparent PNG: '+item.id+' '+JSON.stringify(m));
  const rect=item.extraction.normalizedRect;
  if(rect.length!==4||rect.some(v=>!Number.isFinite(v)||v<0||v>1)||
      rect[2]<=0||rect[3]<=0||rect[0]+rect[2]>1.001||rect[1]+rect[3]>1.001)
    throw Error('Invalid exact outfit extraction: '+item.id);
  const x=Math.round(rect[0]*m.width),y=Math.round(rect[1]*m.height);
  const w=Math.min(m.width-x,Math.round(rect[2]*m.width)),h=Math.min(m.height-y,Math.round(rect[3]*m.height));
  const rgba=await sharp(bytes).extract({left:x,top:y,width:w,height:h}).ensureAlpha().png().toBuffer();
  // Alpha-only trim preserves authentic RGB character pixels; padding adds only
  // transparent canvas and is not upsampling or a falsely claimed native 4K.
  const trimmed=await sharp(rgba).trim({background:'#00000000',threshold:4}).png().toBuffer();
  const t=await sharp(trimmed).metadata();
  if(t.width<90||t.height<180)throw Error('Isolated character is too small: '+item.id);
  const width=Math.max(700,t.width+80),height=Math.max(1100,t.height+160);
  const left=Math.round((width-t.width)/2);
  const processed=await sharp(trimmed).extend({
    top:55,bottom:height-t.height-55,left,right:width-t.width-left,
    background:{r:0,g:0,b:0,alpha:0}
  }).png().toBuffer();
  const processedSHA256=digest(processed);
  if(hashes.has(processedSHA256))throw Error('Identical art assigned to multiple card IDs');
  hashes.add(processedSHA256);
  const old=lock.sources[item.id];
  if(old&&!refresh&&(old.originalSHA256!==sourceSHA256||old.processedSHA256!==processedSHA256||
    old.width!==width||old.height!==height))throw Error('Original or derived artwork changed: '+item.id);
  const localPath='artwork/one-piece/'+item.id+'.png';
  await atomic(inside(localPath),processed);
  const design=designForRank(item.power.rank);
  const source={provider:item.original.provider,page:item.original.page,image:item.original.image,
    localPath,sha256:processedSHA256,width,height,rights:item.original.rights,
    usage:item.original.usage,artist:item.original.artist,credit:item.original.credit,
    nativeOriginal:{sha256:sourceSHA256,width:m.width,height:m.height},
    isolatedPixels:[t.width,t.height],extraction:item.extraction.normalizedRect,
    note:'Original Film Red alpha pixels isolated without AI or upscaling'};
  CATALOG.cards.push({id:item.id,number:item.number,character:item.character,
    name:item.name,title:item.title,form:item.form,formId:item.formId,
    aliases:item.aliases,designs:[design],status:'candidate',source,
    recipe:{crop:[0,0,width,height],bottomPadding:0.15,edgeFade:0.02,
      bottomFade:0.05,focal:[0.5,0.31],artworkPlacement:{
      [design]:{x:-70,y:55,width:1120}
    }}});
  POWER.assessments[item.id]={scale:POWER.scale,status:'proposed',
    rank:item.power.rank,formId:item.formId,artworkSHA256:processedSHA256,
    universe:POWER.universe,continuity:item.continuity,scope:item.power.scope,
    basis:item.power.basis,limits:item.power.limits,references:item.power.references};
  if(writeLock)lock.sources[item.id]={originalSHA256:sourceSHA256,processedSHA256,
    width,height,nativeOriginal:[m.width,m.height],isolatedPixels:[t.width,t.height],url:item.original.image};
  console.log('PIN '+item.id+' '+m.width+'x'+m.height+' native; isolated '+t.width+'x'+t.height+
    '; canvas '+width+'x'+height+'; rank '+item.power.rank+'; original '+sourceSHA256.slice(0,16));
}
await atomic(inside('catalog/one-piece.json'),JSON.stringify(CATALOG,null,2)+'\n');
await atomic(inside('catalog/power/one-piece.json'),JSON.stringify(POWER,null,2)+'\n');
if(writeLock)await atomic(pinsFile,JSON.stringify(lock,null,2)+'\n');
console.log('Created '+CATALOG.cards.length+' authentic source-bound ONE PIECE NIGHT candidates. NOT visually approved/published.');
