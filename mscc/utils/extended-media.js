import { spawn } from 'node:child_process'
import { mkdtemp, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import { runFfmpeg, mediaToSticker } from './media-conversion.js'

const VIDEO_MAX = 20 * 1024 * 1024
const IMAGE_MAX = 12 * 1024 * 1024
const MAX_OUTPUT = 19 * 1024 * 1024
let activeMediaJob = 0

export async function withExtendedMediaJob(work) {
  if (activeMediaJob >= 1) throw new Error('Night is processing another media job. Please try again shortly.')
  activeMediaJob++
  try { return await work() } finally { activeMediaJob-- }
}

export async function withMediaFiles(label, work) {
  const dir = await mkdtemp(join(tmpdir(), 'night-' + label + '-'))
  try { return await work(dir) } finally { await rm(dir, {recursive:true, force:true}).catch(()=>{}) }
}

export function validateMediaSize(buffer, kind='video') {
  const cap = kind === 'image' ? IMAGE_MAX : VIDEO_MAX
  if (!Buffer.isBuffer(buffer) || !buffer.length) throw new Error('Reply to a valid media message.')
  if (buffer.length > cap) throw new Error('Media is too large. Limit: ' + Math.floor(cap/1024/1024) + ' MB.')
}

export async function checkedOutput(path) {
  const size = (await stat(path)).size
  if (!size || size > MAX_OUTPUT) throw new Error('Processed media exceeds WhatsApp delivery size. Try a shorter clip.')
  return readFile(path)
}

export function runMediaProcess(bin, args, { timeoutMs=30000, maxStdout=512000 }={}) {
  return new Promise((resolve,reject)=>{
    const proc = spawn(bin,args,{stdio:['ignore','pipe','pipe']})
    let stdout='',stderr='',settled=false,timedOut=false
    const timer=setTimeout(()=>{timedOut=true;proc.kill('SIGKILL')},timeoutMs)
    timer.unref?.()
    function done(error) {
      if (settled) return
      settled=true
      clearTimeout(timer)
      if (error) reject(error)
      else resolve(stdout)
    }
    proc.stdout.on('data',data=>{
      stdout+=data.toString()
      if (stdout.length>maxStdout) proc.kill('SIGKILL')
    })
    proc.stderr.on('data',data=>{stderr=(stderr+data.toString()).slice(-1200)})
    proc.on('error',error=>done(error?.code==='ENOENT'
      ? new Error(bin+' is not installed. Configure the required tool on the Night server.')
      : error))
    proc.on('close',code=>done(timedOut
      ? new Error('Media processing exceeded its time limit.')
      : code===0 ? null : new Error(stderr.trim().slice(-320) || 'Media processing failed.')))
  })
}

async function writeInput(dir,buffer,extension) {
  const path=join(dir,'input.'+extension)
  await writeFile(path,buffer)
  return path
}

export async function removeImageBackground(buffer) {
  validateMediaSize(buffer,'image')
  return withMediaFiles('removebg',async dir=>{
    const input=await writeInput(dir,buffer,'bin')
    const normalized=join(dir,'prepared.png')
    const output=join(dir,'transparent.png')
    await runFfmpeg(['-i',input,'-frames:v','1','-vf',
      'scale=1024:1024:force_original_aspect_ratio=decrease',normalized],{timeoutMs:20000})
    // u2netp is a lightweight local model. No user images are uploaded to an AI API.
    await runMediaProcess(process.env.REMBG_PATH || '/opt/mscc/tools/rembg-venv/bin/rembg',
      ['i','-m','u2netp',normalized,output],{timeoutMs:120000})
    return checkedOutput(output)
  })
}

function sharpnessScore(canvas) {
  const context=canvas.getContext('2d')
  const {data,width,height}=context.getImageData(0,0,canvas.width,canvas.height)
  const grayscale=new Uint8Array(width*height)
  let bright=0
  for (let i=0;i<grayscale.length;i++) {
    const j=i*4
    const lum=(data[j]*54+data[j+1]*183+data[j+2]*19)>>8
    grayscale[i]=lum
    bright+=lum
  }
  // Discrete Laplacian variance detects detail better than brightness/contrast alone.
  let sum=0,sumSq=0,count=0
  for (let y=1;y<height-1;y+=2) for (let x=1;x<width-1;x+=2) {
    const i=y*width+x
    const lap=4*grayscale[i]-grayscale[i-1]-grayscale[i+1]-grayscale[i-width]-grayscale[i+width]
    sum+=lap;sumSq+=lap*lap;count++
  }
  const variance=sumSq/count-(sum/count)**2
  const avg=bright/grayscale.length
  return variance * (avg<20||avg>240?0.15:1)
}

export async function chooseBestFrames(files, count=1, {imageLoader=loadImage}={}) {
  const scored=[]
  for (const [index,path] of files.entries()) {
    const img=await imageLoader(await readFile(path))
    const canvas=createCanvas(img.width,img.height)
    const g=canvas.getContext('2d')
    g.drawImage(img,0,0)
    scored.push({ index, seconds:1.5+index*3, score:sharpnessScore(canvas) })
  }
  scored.sort((a,b)=>b.score-a.score)
  const picked=[]
  for (const row of scored) {
    if (picked.every(other=>Math.abs(other.index-row.index)>=2)) picked.push(row)
    if (picked.length===count) break
  }
  return picked.sort((a,b)=>a.seconds-b.seconds)
}

export async function bestVideoFrames(buffer, count=1) {
  validateMediaSize(buffer)
  if (!Number.isInteger(count) || count<1 || count>5) throw new Error('Use .bestframe [1-5].')
  return withMediaFiles('bestframe',async dir=>{
    const input=await writeInput(dir,buffer,'mp4')
    const thumbnail=join(dir,'frame-%03d.jpg')
    await runFfmpeg(['-i',input,'-t','24','-vf','fps=1/3,scale=320:-2',
      '-frames:v','8','-q:v','4',thumbnail],{timeoutMs:35000})
    const names=(await readdir(dir)).filter(x=>/^frame-\d{3}\.jpg$/.test(x)).sort()
    if (!names.length) throw new Error('Could not extract usable video frames.')
    const picks=await chooseBestFrames(names.map(name=>join(dir,name)), Math.min(count,names.length))
    const results=[]
    for(const selection of picks) {
      const output=join(dir,'best-'+results.length+'.jpg')
      await runFfmpeg(['-ss',String(selection.seconds),'-i',input,'-frames:v','1',
        '-q:v','3',output],{timeoutMs:20000})
      results.push({buffer:await checkedOutput(output),at:selection.seconds})
    }
    return results
  })
}

export async function reverseClip(buffer,{kind='video'}={}) {
  validateMediaSize(buffer)
  if (!['video','audio'].includes(kind)) throw new Error('Reply to an audio or video file.')
  return withMediaFiles('reverse',async dir=>{
    const input=await writeInput(dir,buffer,kind==='video'?'mp4':'audio')
    const output=join(dir,kind==='video'?'reverse.mp4':'reverse.mp3')
    if (kind==='audio') {
      await runFfmpeg(['-t','40','-i',input,'-vn','-af','areverse',
        '-c:a','libmp3lame','-b:a','128k',output],{timeoutMs:55000})
    } else {
      const ffprobe=process.env.FFPROBE_PATH || 'ffprobe'
      let hasAudio=false
      try {
        hasAudio=Boolean((await runMediaProcess(ffprobe,[
          '-v','error','-select_streams','a','-show_entries','stream=index',
          '-of','csv=p=0',input],{timeoutMs:10000})).trim())
      } catch {}
      await runFfmpeg(['-t','14','-i',input,'-vf',
        'fps=12,scale=480:-2,reverse,format=yuv420p',
        ...(hasAudio ? ['-af','areverse','-c:a','aac','-b:a','96k'] : ['-an']),
        '-c:v','libx264','-preset','ultrafast','-crf','28','-movflags','+faststart',
        output],{timeoutMs:80000})
    }
    return checkedOutput(output)
  })
}

export async function stabilizeVideo(buffer) {
  validateMediaSize(buffer)
  return withMediaFiles('stabilize',async dir=>{
    const input=await writeInput(dir,buffer,'mp4')
    const output=join(dir,'stabilized.mp4')
    const transform=join(dir,'motion.trf')
    const common=['-t','24','-i',input,'-map','0:v:0','-map','0:a?',
      '-c:v','libx264','-preset','veryfast','-crf','25',
      '-c:a','aac','-movflags','+faststart']
    let stabilizedWith='vidstab'
    try {
      await runFfmpeg(['-t','24','-i',input,'-vf',
        'scale=640:-2,vidstabdetect=shakiness=5:accuracy=8:result='+transform,
        '-an','-f','null','-'],{timeoutMs:40000})
      await runFfmpeg([...common.slice(0,4),'-vf',
        'scale=640:-2,vidstabtransform=input='+transform+':smoothing=18:optzoom=1',
        ...common.slice(4),output],{timeoutMs:65000})
    } catch {
      stabilizedWith='deshake'
      await runFfmpeg([...common.slice(0,4),'-vf',
        'scale=640:-2,deshake=rx=32:ry=32:edge=mirror',
        ...common.slice(4),output],{timeoutMs:60000})
    }
    return {buffer:await checkedOutput(output),method:stabilizedWith}
  })
}

export function emojiPairFromText(raw) {
  const parts=[...new Intl.Segmenter('en',{granularity:'grapheme'}).segment(String(raw||'').replace(/\+/g,' '))]
    .map(x=>x.segment).filter(x=>!/^\s+$/.test(x))
  if(parts.length!==2 || parts.some(x=>!/[\p{Extended_Pictographic}\p{Regional_Indicator}]/u.test(x)))
    throw new Error('Use exactly two emojis, for example .emojimix 😍 🔥')
  return parts
}

export function twemojiCodepoint(emoji) {
  const points=[...emoji].map(c=>c.codePointAt(0)).filter(c=>c!==0xfe0f)
  if (!points.length || points.length>12) throw new Error('Unsupported emoji sequence.')
  return points.map(c=>c.toString(16)).join('-')
}

async function fetchEmojiArtwork(icon,{fetcher=fetch}={}) {
  const code=twemojiCodepoint(icon)
  // Fixed host and generated hexadecimal path: no user-controlled URLs.
  const url='https://cdn.jsdelivr.net/gh/jdecked/twemoji@17.0.3/assets/72x72/'+code+'.png'
  const response=await fetcher(url,{signal:AbortSignal.timeout(10000)})
  if(!response.ok) throw new Error('Twemoji artwork is unavailable for '+icon+'.')
  const bytes=Buffer.from(await response.arrayBuffer())
  if(bytes.length<50 || bytes.length>250000) throw new Error('Unexpected emoji asset size.')
  return bytes
}

export async function combinedEmojiSticker(pair,{fetcher=fetch}={}) {
  const [left,right]=pair
  if(!left || !right) throw new Error('Two emojis are required.')
  const [a,b]=await Promise.all([fetchEmojiArtwork(left,{fetcher}),fetchEmojiArtwork(right,{fetcher})])
  const [img1,img2]=await Promise.all([loadImage(a),loadImage(b)])
  const canvas=createCanvas(512,512),g=canvas.getContext('2d')
  // Interlocking emoji collage, transparent edges, and a small centered sparkle.
  g.translate(256,256)
  g.rotate(-0.16)
  g.drawImage(img1,-220,-156,290,290)
  g.rotate(0.32)
  g.drawImage(img2,-66,-130,290,290)
  g.rotate(-0.16)
  g.fillStyle='#fff5a5'
  for(const [x,y,r] of [[0,-158,10],[-175,155,7],[172,138,8]]) {
    g.beginPath()
    g.moveTo(x,y-r*2);g.lineTo(x+r*0.5,y-r*0.5);g.lineTo(x+r*2,y)
    g.lineTo(x+r*0.5,y+r*0.5);g.lineTo(x,y+r*2)
    g.lineTo(x-r*0.5,y+r*0.5);g.lineTo(x-r*2,y);g.lineTo(x-r*0.5,y-r*0.5)
    g.closePath();g.fill()
  }
  // Existing Night sticker encoder preserves the original rounded-corner format.
  return mediaToSticker(canvas.toBuffer('image/png'),{
    packName:'Night Emoji Mix • Twemoji',
    publisher:'Twemoji (CC BY 4.0)',
  })
}

const DISPLAY_EXIF=[
  ['FileType','Format'],['ImageWidth','Width'],['ImageHeight','Height'],
  ['Make','Camera maker'],['Model','Camera model'],
  ['LensModel','Lens'],['DateTimeOriginal','Captured'],
  ['ISO','ISO'],['ExposureTime','Shutter'],['FNumber','Aperture'],
  ['FocalLength','Focal length'],['Orientation','Orientation'],
  ['Software','Software'],['ColorSpace','Color space'],
]
export function formatSafeExif(row) {
  if(!row || typeof row!=='object') return 'Could not read image metadata.'
  const lines=DISPLAY_EXIF.filter(([key])=>row[key]!==undefined&&row[key]!==null)
    .map(([key,label])=>'*'+label+':* '+String(row[key]).replace(/[\r\n]/g,' ').slice(0,140))
  return ['📷 *Image metadata*',...lines,
    lines.length?'':'No EXIF information was retained by WhatsApp.',
    '_GPS coordinates and sensitive identifiers are never displayed._'].filter(Boolean).join('\n')
}
export async function imageExif(buffer,{mimeType='image/jpeg'}={}) {
  validateMediaSize(buffer,'image')
  return withMediaFiles('exif',async dir=>{
    const ext=/png/i.test(mimeType)?'png':/webp/i.test(mimeType)?'webp':'jpg'
    const file=await writeInput(dir,buffer,ext)
    const fields=DISPLAY_EXIF.map(([key])=>'-'+key)
    const stdout=await runMediaProcess(process.env.EXIFTOOL_PATH||'exiftool',
      ['-json','-n',...fields,file],{timeoutMs:15000})
    const rows=JSON.parse(stdout)
    return formatSafeExif(rows?.[0])
  })
}
