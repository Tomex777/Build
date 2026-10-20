import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,atomic,digest} from './catalogue.mjs';
import {prepareDepthLayers} from './depth-layers.mjs';

export async function download(url,{fetchImpl=fetch,limit=16*1024*1024}={}) {
  const response=await fetchImpl(url,{redirect:'error',signal:AbortSignal.timeout(25000)});
  if(!response.ok) throw Error(`Artwork HTTP ${response.status}`);
  const chunks=[];let length=0;
  for await(const chunk of response.body){length+=chunk.length;if(length>limit)throw Error('Artwork exceeds size limit');chunks.push(chunk);}
  return Buffer.concat(chunks);
}
export async function acquireArtwork(card,{fetchImpl=fetch}={}) {
  const s=card.source, file=inside(s.localPath);
  let bytes;
  try {bytes=await fs.readFile(file);} catch(e) {
    if(e.code!=='ENOENT')throw e;
    bytes=await download(s.image,{fetchImpl});
    if(digest(bytes)!==s.sha256)throw Error('Downloaded artwork differs from the pinned source');
    await atomic(file,bytes);
  }
  if(digest(bytes)!==s.sha256)throw Error('Artwork checksum mismatch');
  const metadata=await sharp(bytes,{limitInputPixels:30e6}).metadata();
  if(!['png','jpeg','webp'].includes(metadata.format)||metadata.width!==s.width||metadata.height!==s.height||s.width<500||s.height<(card.universe==='eminence'&&s.provider==='master-of-garden-official-sp'&&s.width>=768?800:900)||(!metadata.hasAlpha&&!card.recipe.sourceMatte))throw Error('Artwork resolution, transparency or format failed');
  const [left,top,width,height]=card.recipe.crop;
  const {data,info}=await sharp(bytes).extract({left,top,width,height}).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  // Opaque official posters retain their original bytes/RGB. An explicit,
  // source-bound matte removes the published background before composition.
  if(card.recipe.sourceMatte){
    const spec=card.recipe.sourceMatte;
    if(spec.sourceSHA256!==s.sha256||JSON.stringify(spec.crop)!==JSON.stringify(card.recipe.crop))throw Error('Source matte belongs to another artwork or crop');
    const mask=await fs.readFile(inside(spec.localPath));
    if(digest(mask)!==spec.sha256)throw Error('Source matte checksum mismatch');
    const m=await sharp(mask).metadata();
    if(m.width!==width||m.height!==height)throw Error('Source matte dimensions differ from crop');
    const matte=await sharp(mask).ensureAlpha().raw().toBuffer();
    for(let i=3;i<data.length;i+=4)data[i]=Math.round(data[i]*matte[i]/255);
  }
  const originalPixels=card.recipe.depthLayers?Buffer.from(data):null;
  let visible=0,clear=0;
  for(let i=3;i<data.length;i+=4){if(data[i]>32)visible++;else clear++;}
  if(visible<width*height*.08||clear<width*height*.005)throw Error('Artwork crop has insufficient subject or transparency');
  const edge=(card.recipe.edgeFade??0)*Math.min(width,height), bottom=(card.recipe.bottomFade??0)*height;
  const smooth=t=>{t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);};
  for(let y=0;y<height;y++)for(let x=0;x<width;x++){
    const a=(edge?smooth(Math.min(x,width-1-x,y)/edge):1)*(bottom?smooth((height-1-y)/bottom):1);
    data[(y*width+x)*4+3]=Math.round(data[(y*width+x)*4+3]*a);
  }
  const pad=Math.round(height*(card.recipe.bottomPadding??.28));
  const art=await sharp(data,{raw:info}).extend({top:0,left:0,right:0,bottom:pad,background:{r:0,g:0,b:0,alpha:0}}).png().toBuffer();
  const depthLayers=await prepareDepthLayers(card,originalPixels,info,pad);
  const fonts={};
  for(const [key,name] of Object.entries({display:'Display',sans:'Sans',italic:'Italic',condensed:'BebasNeue',black:'ArchivoBlack'}))fonts[key]='data:font/ttf;base64,'+(await fs.readFile(inside(`renderer/assets/fonts/${name}.ttf`))).toString('base64');
  return {art:'data:image/png;base64,'+art.toString('base64'),depthLayers,artWidth:width,artHeight:height+pad,croppedArtworkDimensions:[width,height],focal:[card.recipe.focal[0],card.recipe.focal[1]*height/(height+pad)],fonts,title:card.name+' — '+card.form,artist:s.artist,license:s.credit,rights:s.rights,sourceUrl:s.page,licenseUrl:s.page,outputLicense:s.usage};
}
