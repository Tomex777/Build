#!/usr/bin/env node
// Research-only audit: official Fantastic Days game sprites from an unofficial archive.
// NO commercial reuse grant. Do not silently substitute them for LN/anime appearances.
import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from '../src/catalogue.mjs';
const ids={kazuma:100,aqua:101,megumin:102,darkness:103,chris:104,wiz:105,yunyun:106,iris:107,komekko:108,cecily:109,arue:110,mitsurugi:111,dust:112,rain:125,vanir:128};
const root='https://raw.githubusercontent.com/HaiKonofanDesu/konofan-assets-jp-sortet/main/Assets/AddressableAssetsStore/UnityAssets/Battle/Sprite/CharacterImage/';
const results=[],thumbs=[];
for(const [character,id] of Object.entries(ids)){
  const url=root+'character_'+id+'.png';
  try{
    const res=await fetch(url,{redirect:'error',signal:AbortSignal.timeout(20000)});
    if(!res.ok)throw Error('HTTP '+res.status);
    const bytes=Buffer.from(await res.arrayBuffer());
    if(bytes.length>5*1024*1024)throw Error('Large game art unexpected');
    const meta=await sharp(bytes).metadata(),stats=await sharp(bytes).ensureAlpha().stats();
    const box=await sharp(bytes).ensureAlpha().trim({background:'#00000000',threshold:10}).metadata().catch(()=>null);
    const entry={character,id,url,source:'KonoSuba Fantastic Days extracted game image, unofficial archive; copyrighted reference only',
      sha256:digest(bytes),bytes:bytes.length,width:meta.width,height:meta.height,format:meta.format,hasAlpha:meta.hasAlpha,
      alphaMin:stats.channels[3].min,alphaMax:stats.channels[3].max,cutoutDimensions:box?[box.width,box.height]:null,
      eligible:meta.width>=500&&meta.height>=900&&meta.hasAlpha&&stats.channels[3].min<200};
    results.push(entry);
    const png=await sharp(bytes).resize({width:185,height:275,fit:'contain',background:'#151b20'}).flatten({background:'#151b20'}).png().toBuffer();
    thumbs.push({input:png,left:12+(results.length-1)%5*200,top:8+Math.floor((results.length-1)/5)*318});
    console.log(character+' '+meta.width+'x'+meta.height+' alpha='+meta.hasAlpha+' eligible='+entry.eligible);
  }catch(e){results.push({character,id,url,error:e.message});console.warn(character+' ERROR '+e.message)}
}
await atomic(inside('catalog/konosuba-game-audit.json'),JSON.stringify({source:root,rights:'Unofficial extracted archive of copyright-protected licensed game assets; audit only',results},null,2)+'\n');
if(thumbs.length){
  const out=await sharp({create:{width:1000,height:960,channels:4,background:'#151b20'}}).composite(thumbs).png().toBuffer();
  await atomic(inside('dist/konosuba-fantastic-days-contact.png'),out);
}
