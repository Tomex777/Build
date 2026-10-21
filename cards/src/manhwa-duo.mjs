import sharp from 'sharp';

/**
 * Stage two individually pinned transparent cutouts into the existing NIGHT
 * single-hero renderer. This is a compositing derivative, not a new image
 * source or an art-licensing shortcut. Each input is retained separately in
 * the catalogue/lock, and its SHA must pass before this function is called.
 *
 * The inner NIGHT card frame is drawn by the existing renderer over the
 * open-cutout composite. It is NOT a generic split-panel romance template.
 */
export const ROMANCE_STAGE=Object.freeze({width:1600,height:2200,subjectHeight:1675});

const POSITIONS=Object.freeze({
  left: {x:0,y:55,width:930},
  right:{x:665,y:0,width:930}
});
const isImageData = input => typeof input==='string' && /^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(input);
function position(value,kind){
  if(!value || !Number.isInteger(value.x) || !Number.isInteger(value.y)
    || !Number.isInteger(value.width)
    || value.x < -400 || value.x > 1300 || value.y < -250 || value.y > 1200
    || value.width < 500 || value.width > 1500)
    throw Error('Invalid romance placement for '+kind);
  return value;
}
export function validateRomanceComposition(composition) {
  if(composition?.mode!=='two-subject-source-locked')
    throw Error('Two-subject romance cannot be flattened without composition provenance');
  position(composition.left,'left');position(composition.right,'right');
  if(composition.left.x >= composition.right.x
    || composition.left.x+composition.left.width<composition.right.x+140)
    throw Error('Romance figures must overlap in a designed pair, not become two unrelated cards');
  return composition;
}
async function decodePNG(value) {
  if(!isImageData(value)) throw Error('Romance subjects must be separate PNG alpha cutouts');
  const bytes=Buffer.from(value.slice('data:image/png;base64,'.length),'base64');
  const meta=await sharp(bytes,{limitInputPixels:30_000_000}).metadata();
  if(meta.format!=='png'||!meta.hasAlpha||meta.width<1||meta.height<1)
    throw Error('Romance composition requires transparent PNG layers');
  const stats=await sharp(bytes).ensureAlpha().stats();
  if(stats.channels[3].min>32||stats.channels[3].max<200)
    throw Error('Romance layer is missing meaningful transparent and opaque pixels');
  return {bytes,meta};
}
async function toLayer(decoded,xy){
  const image=await sharp(decoded.bytes).resize({width:xy.width}).png().toBuffer();
  const metadata=await sharp(image).metadata();
  if(xy.x+metadata.width<=0 || xy.y+metadata.height<=0
    || xy.x>=ROMANCE_STAGE.width || xy.y>=ROMANCE_STAGE.subjectHeight)
    throw Error('Romance subject lies outside frame stage');
  return {input:image,left:xy.x,top:xy.y,width:metadata.width,height:metadata.height};
}
export async function composeRomanceCutouts({left,right,composition}){
  validateRomanceComposition(composition);
  const [first,second]=await Promise.all([decodePNG(left),decodePNG(right)]);
  const [l,r]=await Promise.all([
    toLayer(first,composition.left),toLayer(second,composition.right)
  ]);
  // Strictly work in an intentionally larger canvas; keep both independently
  // composited subjects and a naturally transparent lower fade margin.
  // Sharp composite rejects negative coordinates, so trim off-canvas portions.
  const crop=async layer=>{
    const x=Math.max(0,layer.left),y=Math.max(0,layer.top);
    const xr=Math.min(ROMANCE_STAGE.width,layer.left+layer.width);
    const yr=Math.min(ROMANCE_STAGE.subjectHeight,layer.top+layer.height);
    if(xr-x<180||yr-y<180)throw Error('Romance subject is excessively cropped');
    const l=Math.max(0,-layer.left),t=Math.max(0,-layer.top);
    return {input:await sharp(layer.input).extract({left:l,top:t,width:xr-x,height:yr-y}).png().toBuffer(),left:x,top:y};
  };
  const [cl,cr]=await Promise.all([crop(l),crop(r)]);
  const bytes=await sharp({create:{width:ROMANCE_STAGE.width,height:ROMANCE_STAGE.height,
    channels:4,background:{r:0,g:0,b:0,alpha:0}}})
    .composite([cl,cr]).png().toBuffer();
  return {art:'data:image/png;base64,'+bytes.toString('base64'),
    artWidth:ROMANCE_STAGE.width,artHeight:ROMANCE_STAGE.height,
    croppedArtworkDimensions:[ROMANCE_STAGE.width,ROMANCE_STAGE.subjectHeight],
    focal:[.5,.35],leftBounds:[l.left,l.top,l.width,l.height],
    rightBounds:[r.left,r.top,r.width,r.height],
    subjectCount:2,derivative:'two-source-pinned-composite'};
}
export function romanceDefaultComposition(){
  return {mode:'two-subject-source-locked',left:{...POSITIONS.left},right:{...POSITIONS.right}};
}
