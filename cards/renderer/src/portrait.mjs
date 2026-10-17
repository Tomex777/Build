import {renderDepthCard, setDepthLight} from './depth.mjs';

export const PORTRAIT_WIDTH=1000;
export const PORTRAIT_HEIGHT=1600;
const number=x=>String(Math.round(x*1e6)/1e6);

/**
 * Extend the current design to a taller card without changing the base renderer.
 * The frame and materials follow the new canvas. Illustration scales equally
 * in X/Y (a centered cover crop); lettering retains its original proportions.
 */
export function renderPortraitCard(def,assets,{height=PORTRAIT_HEIGHT,removeEmblem=true,...options}={}){
 if(!Number.isInteger(height)||height<1400||height>1800)throw new RangeError('Portrait height must be an integer from 1400 to 1800');
 const scale=height/1400,side=options.side??'front';
 const prefix=options.prefix??`portrait-${def.id}-${height}-${side}-`;
 let svg=renderDepthCard(def,assets,{...options,prefix});
 if(removeEmblem&&side==='front'){
  const emblem=/<g transform="translate\(879 1190\)"[^>]*>[\s\S]*?<\/g>/;
  if(!emblem.test(svg))throw new Error('The base emblem changed; review the portrait adapter');
  svg=svg.replace(emblem,'');
 }
 if(scale!==1){
  let images=0;
  svg=svg.replace(/<image\b([^>]*\bid="[^"]*subject"[^>]*)\/>/g,(_,attributes)=>{
   images++;
   return `<image transform="matrix(${number(scale)} 0 0 1 ${number((1-scale)*500)} 0)"${attributes}/>`;
  });
  if(images!==1)throw new Error('Expected one character illustration');
  svg=svg.replace(/<text\b([^>]*)>/g,(_,attributes)=>{
   const y=Number(attributes.match(/\by="([^"]+)"/)?.[1]);
   if(!Number.isFinite(y)||/\btransform=/.test(attributes))throw new Error('Unexpected base typography');
   return `<text transform="translate(0 ${number(y)}) scale(1 ${number(1/scale)}) translate(0 ${number(-y)})"${attributes}>`;
  });
  const boundary='</defs><g clip-path=';
  if(!svg.includes(boundary))throw new Error('Unexpected base SVG structure');
  svg=svg.replace(boundary,`</defs><g data-portrait-canvas="true" transform="scale(1 ${number(scale)})"><g clip-path=`);
  svg=svg.replace(/<\/g><\/svg>$/,'</g></g></svg>');
 }
 svg=svg.replace('width="1000" height="1400" viewBox="0 0 1000 1400"',`width="1000" height="${height}" viewBox="0 0 1000 ${height}"`);
 return svg.replace('<svg ',`<svg data-portrait-height="${height}" data-artwork-scale="${number(scale)}" data-emblem="${removeEmblem?'removed':'retained'}" `);
}

/** Light values remain normalized within the original drawing coordinates. */
export function setPortraitLight(svg,light){setDepthLight(svg,light);}
