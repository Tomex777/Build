import { existsSync } from 'node:fs'
import { createCanvas, GlobalFonts, loadImage } from '@napi-rs/canvas'
import { contextInfo, normalizedContent, findViewOnceMedia } from './whatsapp/messages.js'

for (const [file, family] of [
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 'Night Capture'],
  ['/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 'Night Capture Bold'],
]) {
  try { if (existsSync(file)) GlobalFonts.registerFromPath(file, family) } catch {}
}

const FONT='"Night Capture", "DejaVu Sans", sans-serif'
const BOLD='"Night Capture Bold", "DejaVu Sans", sans-serif'
const WIDTH=900
const BODY_WIDTH=710
const MAX_TEXT=1800
const MAX_LINES=23

export function extractQuotedCapture(message) {
  const info=contextInfo(message?.message)
  if(!info?.stanzaId || !info?.quotedMessage) return null
  if(findViewOnceMedia(info.quotedMessage)) {
    return {error:'View-once messages cannot be made into quote cards.'}
  }
  const original=normalizedContent(info.quotedMessage)
  if(original?.protocolMessage) return {error:'Deleted and system messages cannot be quoted.'}
  let kind='text'
  let text=String(
    original?.conversation ||
    original?.extendedTextMessage?.text ||
    original?.imageMessage?.caption ||
    original?.videoMessage?.caption ||
    original?.documentMessage?.caption ||
    ''
  ).trim()
  if(!text) {
    if(original?.imageMessage) {kind='image';text='📷 Photo message (image not included)'}
    else if(original?.videoMessage) {kind='video';text='🎬 Video message (video not included)'}
    else if(original?.audioMessage) {kind='audio';text='🎤 Voice note / audio message'}
    else if(original?.documentMessage) {kind='document';text='📄 Document message'}
    else if(original?.stickerMessage) {kind='sticker';text='✨ Sticker message (sticker not included)'}
    else return {error:'That quoted message does not contain readable content.'}
  }
  return {
    text:text.slice(0,MAX_TEXT),
    truncated:text.length>MAX_TEXT,
    participant:String(info.participant||''),
    kind,
  }
}

export function wrapScreenshotText(ctx, source, width=BODY_WIDTH, limit=MAX_LINES) {
  const lines=[]
  let clipped=false
  const segmenter=new Intl.Segmenter('en',{granularity:'grapheme'})
  function chunks(word) {
    const out=[]
    let current=''
    for(const {segment} of segmenter.segment(word)) {
      if(current && ctx.measureText(current+segment).width>width) {
        out.push(current)
        current=segment
      } else current+=segment
    }
    if(current)out.push(current)
    return out
  }
  outer:for(const paragraph of String(source).replace(/\r\n?/g,'\n').split('\n')) {
    if(!paragraph.trim()) {
      lines.push('')
      if(lines.length>=limit) {clipped=true;break}
      continue
    }
    let line=''
    for(const word of paragraph.trim().split(/\s+/)) {
      for(const chunk of chunks(word)) {
        const test=line?line+' '+chunk:chunk
        if(line && ctx.measureText(test).width>width) {
          lines.push(line)
          line=chunk
          if(lines.length>=limit) {clipped=true;break outer}
        } else line=test
      }
    }
    if(line) {
      lines.push(line)
      if(lines.length>=limit) {clipped=true;break}
    }
  }
  if(clipped && lines.length) {
    let last=lines.at(-1)||''
    while(last.length && ctx.measureText(last+'…').width>width) last=last.slice(0,-1)
    lines[lines.length-1]=last+'…'
  }
  return {lines,clipped}
}

function shape(ctx,x,y,w,h,r) {
  ctx.beginPath()
  ctx.roundRect(x,y,w,h,r)
}

function shorten(ctx,value,maxWidth) {
  let text=String(value||'')
  while(text.length>1 && ctx.measureText(text).width>maxWidth) text=text.slice(0,-1)
  return text===value?text:text.replace(/\s+$/,'')+'…'
}

export async function renderScreenshotQuote({
  text,authorName='WhatsApp user',avatarBuffer=null,theme='dark',
  mediaKind='text',truncated=false,
}={}) {
  const light=theme==='light'
  const measure=createCanvas(WIDTH,32).getContext('2d')
  measure.font='31px '+FONT
  const wrapped=wrapScreenshotText(measure,text)
  const lines=wrapped.lines
  if(truncated && lines.length) {
    let end=lines.at(-1)
    while(end.length && measure.measureText(end+'…').width>BODY_WIDTH) end=end.slice(0,-1)
    lines[lines.length-1]=end+'…'
  }
  const lineHeight=49
  const quoteHeight=Math.max(120,lines.length*lineHeight+95)
  const height=Math.min(1750,290+quoteHeight)
  const canvas=createCanvas(WIDTH,height)
  const ctx=canvas.getContext('2d')
  const bg=light?'#eef4f4':'#071318'
  const panel=light?'#ffffff':'#10232a'
  const bubble=light?'#e5f7f3':'#163b3b'
  const fg=light?'#13292f':'#effaf9'
  const muted=light?'#617c80':'#97b6b7'
  const accent=light?'#0b8175':'#64ead3'
  const grad=ctx.createLinearGradient(0,0,WIDTH,height)
  grad.addColorStop(0,bg)
  grad.addColorStop(1,light?'#e3f3f0':'#0d242d')
  ctx.fillStyle=grad;ctx.fillRect(0,0,WIDTH,height)
  // Small ornamental glow keeps the exported image recognizably Night without
  // inventing WhatsApp-native UI or falsifying the original chat's timestamp.
  ctx.save()
  ctx.globalAlpha=light?0.065:0.1
  const radial=ctx.createRadialGradient(735,50,5,735,50,490)
  radial.addColorStop(0,accent)
  radial.addColorStop(1,'transparent')
  ctx.fillStyle=radial;ctx.fillRect(0,0,WIDTH,height)
  ctx.restore()
  shape(ctx,36,38,WIDTH-72,height-76,31)
  ctx.fillStyle=panel;ctx.fill()
  ctx.strokeStyle=light?'#d3e4df':'#1d4044';ctx.lineWidth=2;ctx.stroke()
  shape(ctx,36,38,WIDTH-72,8,6);ctx.fillStyle=accent;ctx.fill()

  const avatarX=80,avatarY=92,avatarSize=82
  ctx.save()
  ctx.beginPath()
  ctx.arc(avatarX+41,avatarY+41,41,0,Math.PI*2)
  ctx.clip()
  let img=null
  if(Buffer.isBuffer(avatarBuffer)&&avatarBuffer.length) {
    try {img=await loadImage(avatarBuffer)} catch {}
  }
  if(img) {
    const scale=Math.max(avatarSize/img.width,avatarSize/img.height)
    const w=img.width*scale,h=img.height*scale
    ctx.drawImage(img,avatarX+(avatarSize-w)/2,avatarY+(avatarSize-h)/2,w,h)
  } else {
    ctx.fillStyle=light?'#bedfdb':'#286e73';ctx.fillRect(avatarX,avatarY,82,82)
    ctx.fillStyle=fg;ctx.textBaseline='middle';ctx.textAlign='center'
    ctx.font='38px '+BOLD
    ctx.fillText(String(authorName||'?').trim().charAt(0).toUpperCase()||'?',avatarX+41,avatarY+42)
  }
  ctx.restore()
  ctx.beginPath();ctx.arc(avatarX+41,avatarY+41,42,0,Math.PI*2)
  ctx.strokeStyle=accent;ctx.lineWidth=3;ctx.stroke()

  ctx.textAlign='left';ctx.textBaseline='alphabetic'
  ctx.font='31px '+BOLD;ctx.fillStyle=fg
  ctx.fillText(shorten(ctx,authorName,570),185,126)
  ctx.font='18px '+FONT;ctx.fillStyle=muted
  ctx.fillText('QUOTED WHATSAPP MESSAGE',185,161)

  const left=76,top=206
  shape(ctx,left,top,WIDTH-left*2,quoteHeight,25)
  ctx.fillStyle=bubble;ctx.fill()
  ctx.fillStyle=accent;ctx.fillRect(left+26,top+32,5,quoteHeight-64)
  ctx.font='62px '+BOLD;ctx.fillStyle=accent
  ctx.fillText('“',left+52,top+75)
  ctx.fillStyle=fg;ctx.font='31px '+FONT
  for(let i=0;i<lines.length;i++) ctx.fillText(lines[i],left+57,top+116+i*lineHeight)
  ctx.textAlign='left';ctx.font='17px '+FONT;ctx.fillStyle=muted
  ctx.fillText('NIGHT  /  SCREENSHOT QUOTE',78,height-69)
  ctx.textAlign='right'
  ctx.fillText(mediaKind==='text'?'TEXT':mediaKind.toUpperCase(),WIDTH-81,height-69)
  return canvas.toBuffer('image/png')
}
