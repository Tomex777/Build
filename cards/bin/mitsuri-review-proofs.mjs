#!/usr/bin/env node
/**
 * Original-source review evidence for the real NIGHT Mitsuri candidate.
 * Inspect the human character rather than accepting numeric QA as artwork review.
 * Makes faithful PNG crops; does not generate, modify, upscale, or approve art.
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';

const out=inside('dist/demon-slayer');
const report=JSON.parse(await fs.readFile(path.join(out,'build.json'),'utf8'));
const catalogue=JSON.parse(await fs.readFile(inside('catalog/demon-slayer.json'),'utf8'));
if(report.series!=='demon-slayer'||report.width!==3000||report.height!==4800||
   report.failures.length||report.cards.length!==1||catalogue.cards.length!==1)
  throw Error('No complete real 3000x4800 Mitsuri source render available for review');
const card=catalogue.cards[0],entry=report.cards[0],id='mitsuri-love-hashira--nocturne';
if(card.id!=='mitsuri-love-hashira'||entry.id!==id||
   entry.artworkSHA256!==card.source.sha256||entry.design!=='nocturne'||
   entry.power?.rank!=='A'||!entry.qa?.characterBreakout?.required||
   entry.qa.characterBreakout.longestCrossing<24)
  throw Error('Wrong artwork/form/finish/grade or missing actual figure breakout proof');

const master=await fs.readFile(path.join(out,entry.png));
if(digest(master)!==entry.pngSHA256)throw Error('Generated master image bytes changed');
const metadata=await sharp(master).metadata();
if(metadata.format!=='png'||metadata.width!==3000||metadata.height!==4800)
  throw Error('Master is not the 3000x4800 real PNG card');
const original=await fs.readFile(inside(card.source.localPath));
if(digest(original)!==card.source.sha256)throw Error('Native source art bytes changed');
const o=await sharp(original).metadata();
if(!o.hasAlpha||o.width!==card.source.width||o.height!==card.source.height)
  throw Error('Original Mitsuri art is no longer a pinned transparent original');

const proofDir=path.join(out,'visual-review');
await fs.mkdir(proofDir,{recursive:true});
const items=[];
const record=async(name,data,detail)=>{
  await atomic(path.join(proofDir,name),data);
  items.push({file:'visual-review/'+name,sha256:digest(data),...detail});
};
await record('00-original-source.png',original,{type:'untouched-official-source',size:[o.width,o.height]});
const phone=await fs.readFile(path.join(out,entry.phone));
const p=await sharp(phone).metadata();
if(p.width!==320||p.height!==512)throw Error('Mobile proof is not 320x512');
await record('01-320px-phone.png',phone,{type:'mobile-proof',size:[320,512]});
const regions=[
 ['02-upper-left.png',0,0],['03-upper-right.png',1500,0],
 ['04-middle-left.png',0,1600],['05-middle-right.png',1500,1600],
 ['06-lower-left.png',0,3200],['07-lower-right.png',1500,3200]
];
for(const [name,left,top] of regions){
  const crop=await sharp(master).extract({left,top,width:1500,height:1600})
    .png().toBuffer();
  await record(name,crop,{type:'native-pixel-master-inspection',offset:[left,top],size:[1500,1600]});
}
await atomic(path.join(proofDir,'review.json'),JSON.stringify({
  card:id,sourceURL:card.source.image,sourceSHA256:card.source.sha256,
  masterSHA256:entry.pngSHA256,qa:entry.qa,artworkRights:card.source.rights,
  reviewStatus:'REQUIRES_HUMAN_VISUAL_REVIEW',automatedApproval:false,
  images:items
},null,2)+'\n');
console.log('Created '+items.length+' REAL ORIGINAL/artifact proofs. No generated art or human approval asserted.');
