import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
const require=createRequire(import.meta.url);
const sharp=require('sharp');
import {
  watermarkCacheKey,makeReusableWatermark,watermarkStaticImage,WatermarkCache
} from './watermark.mjs';

const a='120363456789012345@newsletter',b='120363456789012346@newsletter';
const name='NIGHT 🌙  Ọ̀rẹ́  🇳🇬';
const cache=new WatermarkCache({maxEntries:4,maxBytes:1_000_000});
const calls=[];
const renderer=async({displayName,width,height,tone})=>{
  calls.push(displayName);
  const xml='<svg xmlns="http://www.w3.org/2000/svg" width="'+width+'" height="'+height+'">'+
    '<text x="14" y="'+Math.round(height*.67)+'" fill="'+(tone==='dark'?'black':'white')+
    '" font-family="Noto Sans" font-size="35">NIGHT TEST</text>'+
    '<rect x="'+(width-40)+'" y="20" width="24" height="24" fill="red"/></svg>';
  return sharp(Buffer.from(xml)).png().toBuffer();
};
assert.notEqual(watermarkCacheKey({channelId:a,displayName:name}),watermarkCacheKey({channelId:b,displayName:name}));
assert.notEqual(watermarkCacheKey({channelId:a,displayName:name}),watermarkCacheKey({channelId:a,displayName:name+' '}));
assert.notEqual(watermarkCacheKey({channelId:a,displayName:name}),watermarkCacheKey({channelId:a,displayName:name.replace('🇳🇬','🇬🇧')}));
assert.throws(()=>watermarkCacheKey({channelId:'123@g.us',displayName:name}),/Verified/);
await assert.rejects(()=>makeReusableWatermark({channelId:a,displayName:name,renderText:renderer,sharpLibrary:sharp}),/Color emoji/);
const asset=await makeReusableWatermark({channelId:a,displayName:name,renderText:renderer,
  colorEmojiVerified:true,sharpLibrary:sharp,cache});
assert.deepEqual(calls,[name]);
assert.equal(asset.displayName,name);
const rgba=await sharp(asset.buffer).ensureAlpha().raw().toBuffer({resolveWithObject:true});
let alphaMax=0,hasTransparent=false;
for(let i=3;i<rgba.data.length;i+=4){
  alphaMax=Math.max(alphaMax,rgba.data[i]);hasTransparent||=rgba.data[i]===0;
}
assert.ok(alphaMax<=57&&alphaMax>0,'Whole layer has <= 22% alpha, including red emoji proxy');
assert.ok(hasTransparent,'Transparent background persists');
const again=await makeReusableWatermark({channelId:a,displayName:name,renderText:renderer,
  colorEmojiVerified:true,sharpLibrary:sharp,cache});
assert.strictEqual(again,asset,'Identical channel-name/style uses cached bytes');
assert.equal(calls.length,1);
cache.invalidateChannel(a);
assert.equal(cache.bytes,0);
const source=await sharp({create:{width:1200,height:680,channels:4,background:'#647c93'}}).png().toBuffer();
const result=await watermarkStaticImage({input:source,channelId:b,displayName:name,position:'bottom-right',
  cache,sharpLibrary:sharp,renderText:renderer,colorEmojiVerified:true});
assert.equal(result.position,'bottom-right');assert.equal(result.channelId,b);
assert.equal((await sharp(result.buffer).metadata()).width,1200);
assert.equal((await sharp(result.buffer).metadata()).height,680);
const jpg=await sharp(source).jpeg().toBuffer();
const jr=await watermarkStaticImage({input:jpg,channelId:a,displayName:'Channel A',
  sharpLibrary:sharp,renderText:renderer});
assert.equal((await sharp(jr.buffer).metadata()).format,'jpeg');
const webp=await sharp(source).webp().toBuffer();
const wr=await watermarkStaticImage({input:webp,channelId:a,displayName:'Channel A',
  sharpLibrary:sharp,renderText:renderer});
assert.equal((await sharp(wr.buffer).metadata()).format,'webp');
const tooManyPixels=await sharp({create:{width:3500,height:2000,channels:3,background:'#abc'}}).jpeg().toBuffer();
await assert.rejects(()=>watermarkStaticImage({input:tooManyPixels,channelId:a,
  displayName:'NIGHT',sharpLibrary:sharp,renderText:renderer}),/pixel|limit|bounded/i,
  '7MP image is denied before memory-heavy composition');
const gif=await sharp({create:{width:16,height:16,channels:4,background:'red'}}).gif().toBuffer();
await assert.rejects(()=>watermarkStaticImage({input:gif,channelId:a,displayName:'Hi',sharpLibrary:sharp}),/static/i);
await assert.rejects(()=>watermarkStaticImage({input:Buffer.alloc(8*1024*1024+1),channelId:a,
  displayName:'Hi',sharpLibrary:sharp}),/8 MiB/);
await assert.rejects(()=>makeReusableWatermark({channelId:a,displayName:'Hi',opacity:.95,sharpLibrary:sharp}),/opacity/);
assert.notEqual((await makeReusableWatermark({channelId:b,displayName:'Channel B',
  sharpLibrary:sharp,renderText:renderer,cache})).key,asset.key);

const zwjName='NIGHT 👩🏽‍💻  🌈'; // U+200D is part of the authentic verified channel name.
const noZwj='NIGHT 👩🏽💻  🌈';
assert.notEqual(watermarkCacheKey({channelId:a,displayName:zwjName}),
  watermarkCacheKey({channelId:a,displayName:noZwj}), 'Do not strip ZWJ or flatten emoji sequence');
const rtlName='قناة الأشباح 🌙 NIGHT';
const longName='N'.repeat(98)+'👩🏽‍💻'+' fin';
const exact=[];
const exactRenderer=async values=>{
  exact.push(values.displayName);
  return renderer(values);
};
await makeReusableWatermark({channelId:a,displayName:rtlName,colorEmojiVerified:true,
  sharpLibrary:sharp,renderText:exactRenderer});
await makeReusableWatermark({channelId:a,displayName:longName,colorEmojiVerified:true,
  sharpLibrary:sharp,renderText:exactRenderer});
assert.deepEqual(exact,[rtlName,longName],
  'Renderer receives full Unicode/RTL/grapheme sequence, not sanitised/truncated metadata');

console.log('Channel watermark: PASS — isolation, size limits, Unicode, alpha, cache, JPEG/PNG/WebP, animated rejection');
