// A clean, readable bitmap stops speed-type prompts being trivially copied.
import { createCanvas, GlobalFonts } from '@napi-rs/canvas'
import { existsSync } from 'node:fs'

for(const file of [
 '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
 '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
]) {
  try { if(existsSync(file))GlobalFonts.registerFromPath(file, file.includes('Bold')?'NIGHT Bold':'NIGHT Regular') }
  catch {}
}

const W=1080,H=560
export function renderSpeedTypeChallenge(sentence) {
  const text=String(sentence||'').trim()
  if(!text || text.length>220)throw new Error('Typing prompt unavailable.')
  const canvas=createCanvas(W,H)
  const ctx=canvas.getContext('2d')
  const bg=ctx.createLinearGradient(0,0,W,H)
  bg.addColorStop(0,'#0b1423');bg.addColorStop(.57,'#111a32');bg.addColorStop(1,'#101826')
  ctx.fillStyle=bg;ctx.fillRect(0,0,W,H)
  ctx.fillStyle='rgba(25,214,192,0.09)'
  ctx.beginPath();ctx.arc(940,80,240,0,Math.PI*2);ctx.fill()
  ctx.beginPath();ctx.arc(80,460,190,0,Math.PI*2);ctx.fill()
  ctx.fillStyle='#23e9cb';ctx.fillRect(65,64,8,50)
  ctx.textAlign='left';ctx.textBaseline='alphabetic'
  ctx.fillStyle='#e3edf7';ctx.font='bold 50px "NIGHT Bold", sans-serif'
  ctx.fillText('SPEED TYPE',95,106)
  ctx.fillStyle='#8aa0b7';ctx.font='25px "NIGHT Regular", sans-serif'
  ctx.fillText('TYPE THE SENTENCE SHOWN BELOW',68,160)
  ctx.fillStyle='#16253a'
  ctx.beginPath();ctx.roundRect(60,199,960,251,28);ctx.fill()
  ctx.strokeStyle='#2c4f63';ctx.lineWidth=2;ctx.stroke()
  const words=text.split(/\s+/)
  let lines=[],line=''
  ctx.font='bold 39px "NIGHT Bold", sans-serif'
  for(const word of words){
    const attempt=line?line+' '+word:word
    if(ctx.measureText(attempt).width>880 && line){lines.push(line);line=word}
    else line=attempt
  }
  if(line)lines.push(line)
  if(lines.length>4)throw new Error('Prompt is too long to display.')
  ctx.fillStyle='#ffffff'
  let lineHeight=56,top=325-((lines.length-1)*lineHeight)/2
  lines.forEach((txt,i)=>ctx.fillText(txt,102,top+i*lineHeight))
  ctx.fillStyle='#23e9cb';ctx.font='bold 30px "NIGHT Bold", sans-serif'
  ctx.fillText('35 SECONDS',70,510)
  ctx.fillStyle='#aab9ca';ctx.font='25px "NIGHT Regular", sans-serif'
  ctx.textAlign='right';ctx.fillText('FIRST CORRECT RESPONSE WINS',1013,510)
  return canvas.toBuffer('image/png')
}
