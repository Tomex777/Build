// Unpack an animated WhatsApp WebP sticker into composited PNG frames.
// FFmpeg (including current Ubuntu builds) does not reliably decode the
// ANIM/ANMF WebP container directly. The existing node-webpmux dependency
// decodes the individual frames, including disposal and blending metadata.
import WebP from 'node-webpmux'
import { createCanvas } from '@napi-rs/canvas'
import { writeFile } from 'node:fs/promises'
import { join } from 'node:path'

const MAX_SOURCE=8*1024*1024
const MAX_FRAMES=120
const MAX_PIXELS=1024*1024
const MIN_DELAY_MS=25

export async function extractAnimatedWebpFrames(bytes, directory) {
  if (!Buffer.isBuffer(bytes) || bytes.length<16 || bytes.length>MAX_SOURCE) {
    throw new Error('Sticker media is empty or too large to convert.')
  }
  // node-webpmux requires its WebP decoder to be initialized explicitly.
  await WebP.Image.initLib()
  const image=new WebP.Image()
  await image.load(bytes)
  if (!image.hasAnim || !image.frames?.length) {
    throw new Error('Reply to an animated sticker. Static stickers can be converted with .toimg.')
  }
  if (image.width<1 || image.height<1 || image.width*image.height>MAX_PIXELS ||
      image.frames.length>MAX_FRAMES) {
    throw new Error('Sticker animation is too large or has too many frames.')
  }

  const canvas=createCanvas(image.width,image.height)
  const ctx=canvas.getContext('2d')
  const bg=image.anim?.bgColor
  const bgColor=Array.isArray(bg) && bg.length===4
    ? 'rgba('+bg[0]+','+bg[1]+','+bg[2]+','+(bg[3]/255)+')'
    : 'rgba(0,0,0,0)'
  const clearRegion=(x,y,w,h)=>{
    ctx.clearRect(x,y,w,h)
    if (Array.isArray(bg)) {
      ctx.fillStyle=bgColor
      ctx.fillRect(x,y,w,h)
    }
  }
  clearRegion(0,0,image.width,image.height)

  const entries=[]
  for (let i=0;i<image.frames.length;i++) {
    const frame=image.frames[i]
    const {x=0,y=0,width,height}=frame
    if (!width||!height||x<0||y<0||x+width>image.width||y+height>image.height ||
        width*height>MAX_PIXELS) {
      throw new Error('Animated sticker contains an invalid frame.')
    }
    if (frame.blend===false) clearRegion(x,y,width,height)
    const raw=await image.getFrameData(i)
    if (!raw || raw.length!==width*height*4) {
      throw new Error('Cannot decode animated sticker frame '+(i+1)+'.')
    }
    const patch=createCanvas(width,height)
    const patchCtx=patch.getContext('2d')
    const pixelData=patchCtx.createImageData(width,height)
    pixelData.data.set(raw)
    patchCtx.putImageData(pixelData,0,0)
    ctx.drawImage(patch,x,y)
    const filename='frame-'+String(i).padStart(3,'0')+'.png'
    await writeFile(join(directory,filename),canvas.toBuffer('image/png'))
    entries.push({filename,delay:Math.max(MIN_DELAY_MS,Math.min(1000,Number(frame.delay)||100))})
    if (frame.dispose) clearRegion(x,y,width,height)
  }
  const lines=[]
  for(const row of entries) {
    lines.push("file '"+row.filename+"'")
    lines.push('duration '+(row.delay/1000).toFixed(3))
  }
  // The concat demuxer needs a final repeated image to retain the last delay.
  lines.push("file '"+entries[entries.length-1].filename+"'")
  const manifest=join(directory,'frames.ffconcat')
  await writeFile(manifest,lines.join('\n')+'\n','utf8')
  return {manifest,frames:entries.length,width:image.width,height:image.height}
}
