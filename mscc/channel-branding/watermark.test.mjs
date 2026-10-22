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
// EXIF orientation must be reflected in overlay placement and output size;
// a 900x500 JPEG with orientation=6 becomes 500x900 before composition.
const orientedSource=await sharp({create:{width:900,height:500,channels:3,background:'#eeffee'}})
  .jpeg().withMetadata({orientation:6}).toBuffer();
const orientedResult=await watermarkStaticImage({input:orientedSource,channelId:a,
  displayName:'Channel A',sharpLibrary:sharp,renderText:renderer,position:'top-right'});
const orientedMeta=await sharp(orientedResult.buffer).metadata();
assert.equal(orientedMeta.width,500);
assert.equal(orientedMeta.height,900);
const webp=await sharp(source).webp().toBuffer();
const wr=await watermarkStaticImage({input:webp,channelId:a,displayName:'Channel A',
  sharpLibrary:sharp,renderText:renderer});
assert.equal((await sharp(wr.buffer).metadata()).format,'webp');
// Edge score must cover the actual watermark footprint. A visually busy image
// is still rendered for the owner preview, but flagged for human placement QA.
const busyRaw=Buffer.alloc(640*400*3);
for(let y=0;y<400;y++)for(let x=0;x<640;x++){
  // 40px source blocks become ~4px thumbnail stripes; a single-scale
  // 2px sampler previously reported zero busyness due to aliasing.
  const v=(Math.floor(x/40)+Math.floor(y/25))%2?14:242;
  busyRaw.fill(v,(y*640+x)*3,(y*640+x)*3+3);
}
const busyPng=await sharp(busyRaw,{raw:{width:640,height:400,channels:3}}).png().toBuffer();
const busyPreview=await watermarkStaticImage({input:busyPng,channelId:a,displayName:'NIGHT',
  position:'auto',sharpLibrary:sharp,renderText:renderer});
assert.ok(busyPreview.warnings.some(w=>/visually busy/.test(w)),
  'Dense content needs a real placement review, not a false OCR guarantee');
// Extremely tall static images must sample enough overlay-area rows for
// correct bright-background contrast (64x64 fixed thumbnails lose this).
const tallSource=await sharp({create:{width:320,height:2800,channels:3,background:'#eeeeee'}}).png().toBuffer();
const tallResult=await watermarkStaticImage({input:tallSource,channelId:a,
  displayName:'Tall Channel',position:'auto',sharpLibrary:sharp,renderText:renderer});
assert.equal(tallResult.tone,'dark','Bright tall image must receive dark-ink watermark');
assert.ok(['top-left','top-right','bottom-left','bottom-right'].includes(tallResult.position));
const tooManyPixels=await sharp({create:{width:2500,height:1800,channels:3,background:'#abc'}}).jpeg().toBuffer();
await assert.rejects(()=>watermarkStaticImage({input:tooManyPixels,channelId:a,
  displayName:'NIGHT',sharpLibrary:sharp,renderText:renderer}),/pixel|limit|bounded/i,
  '4.5MP image is denied before memory-heavy composition');
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

// An owner-uploaded fallback is an actual transparent overlay, never a solid PNG
// turned into a translucent rectangular obstruction over meme content.
const opaqueFallback=await sharp({create:{width:320,height:80,channels:3,background:'#ffffff'}}).png().toBuffer();
await assert.rejects(()=>makeReusableWatermark({
  channelId:a,displayName:zwjName,approvedFallbackPng:opaqueFallback,
  sharpLibrary:sharp
}),/alpha channel/);
// Reject a PNG which technically has glyph pixels, but is too faint to survive
// applying uniform whole-layer opacity (would silently skip the watermark).
const faintFallback=await sharp(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="320" height="80">'+
  '<text x="20" y="45" font-size="28" fill="#ffffff" fill-opacity="0.05">INVISIBLE</text></svg>')).png().toBuffer();
await assert.rejects(()=>makeReusableWatermark({
  channelId:a,displayName:zwjName,approvedFallbackPng:faintFallback,
  sharpLibrary:sharp
}),/practically invisible/);
const transparentFallback=await sharp({create:{width:320,height:80,channels:4,background:'#00000000'}})
  .composite([{input:Buffer.from('<svg width="320" height="80" xmlns="http://www.w3.org/2000/svg"><text x="20" y="45" font-size="28" fill="#ffffff">OWNER REVIEWED</text></svg>')}])
  .png().toBuffer();
const fallbackAsset=await makeReusableWatermark({
  channelId:a,displayName:zwjName,approvedFallbackPng:transparentFallback,
  sharpLibrary:sharp,colorEmojiVerified:false
});
assert.equal(fallbackAsset.glyphsVerified,true,'Fallback is explicitly owner-reviewed');
assert.ok(fallbackAsset.buffer.length>0);
assert.notEqual(fallbackAsset.key,asset.key,'Fallback content and exact Unicode name contribute to key');
// A white-only fallback loses contrast on pale memes. Approved contrast-specific
// PNG assets select by the measured destination tone without recoloring emoji.
const darkInkFallback=await sharp({create:{width:320,height:80,channels:4,background:'#00000000'}})
  .composite([{input:Buffer.from('<svg width="320" height="80" xmlns="http://www.w3.org/2000/svg"><text x="20" y="45" font-size="28" fill="#151b20">OWNER REVIEWED</text></svg>')}])
  .png().toBuffer();
const contrastPair={light:transparentFallback,dark:darkInkFallback};
const lightInk=await makeReusableWatermark({channelId:a,displayName:zwjName,
  approvedFallbackPng:contrastPair,tone:'light',sharpLibrary:sharp});
const darkInk=await makeReusableWatermark({channelId:a,displayName:zwjName,
  approvedFallbackPng:contrastPair,tone:'dark',sharpLibrary:sharp});
assert.notEqual(lightInk.key,darkInk.key,'Tone and selected approved asset cannot reuse wrong cache');
assert.notDeepEqual(lightInk.buffer,darkInk.buffer,'The approved contrasting ink is actually selected');
await assert.rejects(()=>makeReusableWatermark({channelId:a,displayName:zwjName,
  approvedFallbackPng:{light:transparentFallback},sharpLibrary:sharp}),/light and dark PNG Buffers/);
console.log('Channel watermark: PASS — isolation, size limits, Unicode, alpha, cache, JPEG/PNG/WebP, animated rejection');
