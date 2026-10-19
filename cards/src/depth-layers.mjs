import fs from 'node:fs/promises';
import sharp from 'sharp';
import {inside,digest} from './catalogue.mjs';
import {FRAME_PATHS} from './frame-depth.mjs';

/** Prepare a foreground matte from the pinned original pixels. No replacement
 * or generated RGB pixels are used. */
export async function prepareDepthLayers(card,pixels,info,pad) {
  const layers={};
  for(const [design,spec] of Object.entries(card.recipe.depthLayers??{})){
    if(!FRAME_PATHS[design])throw Error('Depth layers need a framed design');
    if(!Number.isFinite(spec.auraOpacity)||spec.auraOpacity<0||spec.auraOpacity>.4)throw Error('Invalid aura opacity');
    const cropEdgeFade=spec.cropEdgeFade??0;
    if(!Number.isFinite(cropEdgeFade)||cropEdgeFade<0||cropEdgeFade>.12)throw Error('Invalid foreground crop-edge fade');
    const mask=spec.mask;
    if(!mask||mask.sourceSHA256!==card.source.sha256||JSON.stringify(mask.crop)!==JSON.stringify(card.recipe.crop))throw Error('Silhouette mask belongs to another source or crop');
    const bytes=await fs.readFile(inside(mask.localPath));
    if(digest(bytes)!==mask.sha256)throw Error('Silhouette mask checksum mismatch');
    const metadata=await sharp(bytes).metadata();
    if(metadata.width!==info.width||metadata.height!==info.height)throw Error('Silhouette mask dimensions differ from the artwork crop');
    const matte=await sharp(bytes).ensureAlpha().raw().toBuffer();
    const body=Buffer.from(pixels),bottom=(card.recipe.bottomFade??0)*info.height,edge=cropEdgeFade*Math.min(info.width,info.height);
    const smooth=t=>{t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);};
    for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++){
      const i=(y*info.width+x)*4;
      // Feather only where the original illustration meets the crop canvas.
      // The silhouette's interior contours keep their crisp original pixels.
      const t=(bottom?smooth((info.height-1-y)/bottom):1)*(edge?smooth(Math.min(x,info.width-1-x,y)/edge):1);
      body[i+3]=Math.round(body[i+3]*matte[i+3]/255*t);
    }
    const png=await sharp(body,{raw:info}).extend({top:0,left:0,right:0,bottom:pad,background:{r:0,g:0,b:0,alpha:0}}).png().toBuffer();
    layers[design]={foreground:'data:image/png;base64,'+png.toString('base64'),auraOpacity:spec.auraOpacity,maskSHA256:mask.sha256};
  }
  return layers;
}

/** The aura stays inside the architectural opening. Only the isolated figure
 * receives the existing foreground cast shadow, rim and frame overlap. */
export function composeDepthLayers(svg,assets,layer,design) {
  const images=[...svg.matchAll(/<image\b[^>]*\bid="([^"]*(?:subject|hero|characterSource))"[^>]*\/>/g)];
  if(images.length!==1)throw Error('Expected one framed foreground illustration');
  const [definition,sourceId]=images[0],auraId=sourceId+'-aura',clipId=sourceId+'-aura-window';
  const auraDefinition=definition.replace(`id="${sourceId}"`,`id="${auraId}"`).replace(/\bhref="[^"]*"/,`href="${assets.art}"`);
  const opening=FRAME_PATHS[design];
  svg=svg.replace(definition,definition+auraDefinition+`<clipPath id="${clipId}"><path d="${opening}"/></clipPath>`);
  let placed=0;
  svg=svg.replace(/<g\b(?=[^>]*(?:data-depth="subject"|data-layer="(?:hero|character)"))[^>]*>/,subject=>{
    placed++;
    return `<g data-layer="illustration-aura" data-aura-opacity="${layer.auraOpacity}" clip-path="url(#${clipId})" opacity="${layer.auraOpacity}"><use href="#${auraId}"/></g>`+subject;
  });
  if(placed!==1)throw Error('Expected one character depth layer');
  return svg.replace('<svg ',`<svg data-layered-artwork="true" data-foreground-mask-sha256="${layer.maskSHA256}" `);
}
