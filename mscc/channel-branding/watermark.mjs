// NIGHT Channels — bounded reusable watermark PNG and source-image compositing.
// C's publisher MUST still authorize owner, module, account, admin and final send.
import { createHash } from 'node:crypto';
const JID=/^\d{8,25}@newsletter$/;
const MAX_INPUT=8*1024*1024, MAX_PIXELS=12_000_000;
const MAX_RENDER_PIXELS=1024*256, MAX_LAYER_FILE=2*1024*1024;
const POSITIONS=new Set(['auto','top-left','top-right','bottom-left','bottom-right']);
const hasEmoji=text=>/\p{Extended_Pictographic}|\p{Regional_Indicator}/u.test(text);
const hash=value=>createHash('sha256').update(value).digest('hex');
const getSharp=async provided=>provided||(await import('sharp')).default;
const warmedFonts=new WeakSet();

function checkChannel(channelId,displayName) {
  if(typeof channelId!=='string'||!JID.test(channelId)) throw new Error('Verified numeric WhatsApp Channel JID required.');
  if(typeof displayName!=='string'||!displayName.trim()||[...displayName].length>160||
    /[\u0000-\u001f\u007f]/u.test(displayName))throw new Error('Exact verified channel display name required.');
}
function checkOpacity(opacity){
  if(typeof opacity!=='number'||!Number.isFinite(opacity)||opacity<.15||opacity>.30)
    throw new RangeError('Watermark opacity must be between 0.15 and 0.30.');
}
export function watermarkCacheKey({channelId,displayName,styleVersion=1,tone='light',opacity=.22,width=800,height=144}){
  checkChannel(channelId,displayName);checkOpacity(opacity);
  if(!['light','dark'].includes(tone)||!Number.isInteger(styleVersion)||styleVersion<1)throw new Error('Invalid watermark revision or tone.');
  // Exact codepoints: no ASCII transliteration, NFC-folding, emoji/ZWJ stripping or trimming.
  return hash(JSON.stringify([channelId,displayName,styleVersion,tone,opacity,width,height]));
}
export class WatermarkCache {
  constructor({maxEntries=24,maxBytes=12*1024*1024}={}){
    if(!Number.isInteger(maxEntries)||maxEntries<1||maxEntries>128||!Number.isInteger(maxBytes)||maxBytes<1024)
      throw new Error('Invalid watermark cache budget.');
    this.entries=new Map();this.maxEntries=maxEntries;this.maxBytes=maxBytes;this.bytes=0;
  }
  get(key){
    const row=this.entries.get(key);if(!row)return null;
    this.entries.delete(key);this.entries.set(key,row);return row.asset;
  }
  set(key,asset){
    if(!Buffer.isBuffer(asset?.buffer)||asset.buffer.length>this.maxBytes)return false;
    const old=this.entries.get(key);if(old)this.bytes-=old.size;
    this.entries.delete(key);this.entries.set(key,{asset,size:asset.buffer.length});this.bytes+=asset.buffer.length;
    while(this.entries.size>this.maxEntries||this.bytes>this.maxBytes){
      const oldest=this.entries.keys().next().value,row=this.entries.get(oldest);
      this.entries.delete(oldest);this.bytes-=row.size;
    }
    return true;
  }
  invalidateChannel(channelId){
    for(const [key,row] of this.entries)if(row.asset.channelId===channelId){
      this.entries.delete(key);this.bytes-=row.size;
    }
  }
}

/** Skia-based native text renderer; does NOT use Sharp's broken monochrome emoji SVG path.
 * The target server/font set must pass a real color-emoji screenshot acceptance
 * before colorEmojiVerified can be set to true by the caller.
 */
export async function renderUnicodeTextLayer({displayName,width,height,tone,canvasLibrary}){
  const lib=canvasLibrary||(await import('@napi-rs/canvas'));
  if(typeof lib.createCanvas!=='function')throw new Error('Native Unicode canvas unavailable.');
  // Native Skia supports color fonts, but the image depends on the fonts
  // installed/registered on the actual bot host. Callers can register an
  // approved Noto Color Emoji font via lib.GlobalFonts.registerFromPath(...)
  // before starting the worker. Never silently substitute ASCII for emoji.
  if(!warmedFonts.has(lib)){
    lib.GlobalFonts?.loadSystemFonts?.();
    warmedFonts.add(lib);
  }
  const canvas=lib.createCanvas(width,height),ctx=canvas.getContext('2d');
  ctx.clearRect(0,0,width,height);ctx.fillStyle=tone==='dark'?'#141d20':'#f7f9f6';
  ctx.textAlign='center';ctx.textBaseline='middle';
  const family='"Noto Sans", "Noto Color Emoji", sans-serif';
  let px=Math.min(72,Math.floor(height*.59));
  for(;px>=16;px-=2){
    ctx.font='700 '+px+'px '+family;
    if(ctx.measureText(displayName).width<=width-48)break;
  }
  if(px<16)throw new Error('Name too long for legible watermark; use an owner-approved transparent PNG.');
  ctx.fillText(displayName,width/2,height/2);
  const buffer=canvas.toBuffer('image/png');
  return Buffer.isBuffer(buffer)?buffer:Buffer.from(await buffer);
}

export async function makeReusableWatermark({
  channelId,displayName,styleVersion=1,tone='light',opacity=.22,width=800,height=144,
  cache=null,renderText=renderUnicodeTextLayer,canvasLibrary=null,
  approvedFallbackPng=null,colorEmojiVerified=false,sharpLibrary=null
}={}){
  checkChannel(channelId,displayName);checkOpacity(opacity);
  if(!['light','dark'].includes(tone))throw new Error('Invalid watermark tone.');
  if(!Number.isInteger(width)||!Number.isInteger(height)||width<200||height<64||
     width*height>MAX_RENDER_PIXELS)throw new RangeError('Unsafe watermark dimensions.');
  if(approvedFallbackPng&&(!Buffer.isBuffer(approvedFallbackPng)||approvedFallbackPng.length>MAX_LAYER_FILE))
    throw new Error('Owner fallback must be a bounded transparent PNG.');
  if(hasEmoji(displayName)&&!approvedFallbackPng&&!colorEmojiVerified)
    throw new Error('Color emoji glyphs have not passed target-server visual validation; use owner-approved PNG fallback.');
  const key=watermarkCacheKey({channelId,displayName,styleVersion,tone,opacity,width,height})+
    (approvedFallbackPng?':'+hash(approvedFallbackPng):'');
  const hit=cache?.get(key);if(hit)return hit;
  const sharp=await getSharp(sharpLibrary);
  const source=approvedFallbackPng||await renderText({displayName,width,height,tone,canvasLibrary});
  if(!Buffer.isBuffer(source)||source.length>MAX_LAYER_FILE)throw new Error('Watermark source exceeds asset budget.');
  const meta=await sharp(source,{limitInputPixels:approvedFallbackPng?2_000_000:MAX_RENDER_PIXELS}).metadata();
  if(meta.format!=='png'||(meta.pages??1)>1)throw new Error('Watermark must be static transparent PNG.');
  // A manually supplied owner-approved fallback can be larger; resize/letterbox.
  const layer=approvedFallbackPng&& (meta.width!==width||meta.height!==height)
    ?await sharp(source,{limitInputPixels:2_000_000})
      .resize(width,height,{fit:'contain',background:'#00000000'}).png().toBuffer()
    :source;
  if(!approvedFallbackPng&&(meta.width!==width||meta.height!==height))
    throw new Error('Native watermark renderer size mismatch.');
  const raw=await sharp(layer,{limitInputPixels:MAX_RENDER_PIXELS}).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  if(raw.info.channels!==4||raw.info.width!==width||raw.info.height!==height)
    throw new Error('Incorrect RGBA layer dimensions.');
  // Apply opacity AFTER rasterizing, to ALL glyph pixels including colored emoji.
  let ink=0;for(let i=3;i<raw.data.length;i+=4){
    if(raw.data[i]>0)ink++;
    raw.data[i]=Math.round(raw.data[i]*opacity);
  }
  if(ink===0||ink>width*height*.85)
    throw new Error('Watermark is empty or insufficiently transparent; request a new owner overlay.');
  const png=await sharp(raw.data,{raw:{width,height,channels:4}}).png({compressionLevel:8}).toBuffer();
  if(png.length>MAX_LAYER_FILE)throw new Error('Encoded watermark exceeds budget.');
  const asset=Object.freeze({channelId,displayName,styleVersion,tone,opacity,width,height,key,buffer:png,
    glyphsVerified:!hasEmoji(displayName)||colorEmojiVerified||Boolean(approvedFallbackPng)});
  cache?.set(key,asset);
  return asset;
}

function placement(which,w,h,layerW,layerH){
  const gap=Math.max(10,Math.round(Math.min(w,h)*.025));
  return {left:which.endsWith('left')?gap:w-layerW-gap,
    top:which.startsWith('top')?gap:h-layerH-gap};
}
function chooseCorner(data,channels,w,h){
  const corners=['top-left','top-right','bottom-left','bottom-right'];
  let best='bottom-right',bestScore=Infinity;
  const tones={};
  const lum=(x,y)=>{
    const i=(y*w+x)*channels;
    return .2126*data[i]+.7152*data[i+1]+.0722*data[i+2];
  };
  for(const part of corners){
    const x0=part.endsWith('left')?2:Math.round(w*.53),x1=part.endsWith('left')?Math.round(w*.47):w-2;
    const y0=part.startsWith('top')?2:Math.round(h*.70),y1=part.startsWith('top')?Math.round(h*.29):h-2;
    let busy=0,total=0,count=0;
    for(let y=y0;y<y1-1;y+=2)for(let x=x0;x<x1-1;x+=2){
      const l=lum(x,y);total+=l;count++;
      busy+=Math.abs(l-lum(x+1,y))+Math.abs(l-lum(x,y+1));
    }
    tones[part]=count&&total/count>145?'dark':'light';
    if(count&&busy/count<bestScore){bestScore=busy/count;best=part;}
  }
  return {position:best,tones};
}

/** Media is composed in memory, never enqueued or posted here.
 * No OCR; automatic placement is low-detail region heuristic only.
 */
export async function watermarkStaticImage({
  input,channelId,displayName,styleVersion=1,opacity=.22,position='auto',
  cache=null,approvedFallbackPng=null,colorEmojiVerified=false,
  renderText=renderUnicodeTextLayer,canvasLibrary=null,sharpLibrary=null,
  maxOutputBytes=8*1024*1024
}={}){
  if(!Buffer.isBuffer(input)||input.length===0||input.length>MAX_INPUT)
    throw new Error('Static input exceeds 8 MiB or is not a Buffer.');
  checkChannel(channelId,displayName);checkOpacity(opacity);
  if(!POSITIONS.has(position))throw new Error('Invalid watermark position.');
  if(!Number.isInteger(maxOutputBytes)||maxOutputBytes<1024||maxOutputBytes>8*1024*1024)
    throw new Error('Invalid output budget.');
  const sharp=await getSharp(sharpLibrary);
  const meta=await sharp(input,{limitInputPixels:MAX_PIXELS,animated:false}).metadata();
  if(!['jpeg','png','webp'].includes(meta.format)||(meta.pages??1)>1||!meta.width||!meta.height||
    meta.width*meta.height>MAX_PIXELS)
    throw new Error('Only bounded static JPEG/PNG/WebP images. Animated/video requires another pipeline.');
  const rotated=[5,6,7,8].includes(meta.orientation);
  const w=rotated?meta.height:meta.width,h=rotated?meta.width:meta.height;
  if(w<200||h<140)throw new Error('Image too small for a legible watermark.');
  const thumb=await sharp(input,{limitInputPixels:MAX_PIXELS}).rotate()
    .resize(64,64,{fit:'fill'}).removeAlpha().raw().toBuffer({resolveWithObject:true});
  const decision=chooseCorner(thumb.data,thumb.info.channels,thumb.info.width,thumb.info.height);
  const usePosition=position==='auto'?decision.position:position;
  const tone=decision.tones[usePosition];
  const layerWidth=Math.min(800,Math.max(200,Math.floor(w*.67)));
  const layerHeight=Math.max(64,Math.min(144,Math.floor(layerWidth*.18),Math.floor(h*.30)));
  const asset=await makeReusableWatermark({
    channelId,displayName,styleVersion,opacity,tone,width:layerWidth,height:layerHeight,
    cache,renderText,canvasLibrary,approvedFallbackPng,colorEmojiVerified,sharpLibrary:sharp
  });
  const {left,top}=placement(usePosition,w,h,asset.width,asset.height);
  if(left<0||top<0||left+asset.width>w||top+asset.height>h)
    throw new Error('Layer exceeds safe image area.');
  const output=sharp(input,{limitInputPixels:MAX_PIXELS}).rotate().composite([{input:asset.buffer,left,top}]);
  const result=meta.format==='jpeg'?
    await output.jpeg({quality:86,mozjpeg:true}).toBuffer():
    meta.format==='webp'?
      await output.webp({quality:85}).toBuffer():
      await output.png({compressionLevel:8}).toBuffer();
  if(result.length>maxOutputBytes)throw new Error('Composited result exceeds output byte budget.');
  return {buffer:result,format:meta.format,position:usePosition,tone,
    assetKey:asset.key,channelId,displayName,opacity,
    warnings:asset.glyphsVerified?[]:['Glyph coverage still needs target-server review.']};
}
