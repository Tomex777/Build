// Owner-triggered, explicitly opt-in WhatsApp integration tests.
// This is intentionally an ALLOWLIST: never run admin, account, moderation,
// billing, broadcast or arbitrary commands against a real chat.
import { mkdtemp, readFile, rm, writeFile, realpath, stat } from 'node:fs/promises'
import { join, sep } from 'node:path'
import { tmpdir } from 'node:os'
import { createCanvas, loadImage } from '@napi-rs/canvas'
import { PDFDocument } from 'pdf-lib'
import QRCode from 'qrcode'
import { loadCommands } from './command-registry.js'
import { runMediaProcess } from './utils/extended-media.js'
import { mediaToSticker } from './utils/media-conversion.js'
import { clearMediaSession } from './utils/media-toolkit.js'
import { downloadImageUrl } from './utils/remote-image.js'

const PUBLIC = new URL('./commands/', import.meta.url)
const CHAT_GAP_MS = 650
const TEST_TIMEOUT_MS = 110000
const MAX_REPLY = 320
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

// Each selected command runs its REAL handler with a fixed, non-user fixture.
// All unlisted commands remain explicitly unverified, never "passed".
// Explicitly enumerated safe reaction handlers (never the moderation .kick).
// In live mode these fetch real GIF sources and deliver actual rounded stickers.
export const LIVE_REACTION_NAMES = Object.freeze(
  'akick angry baka bite bleh blowkiss blush bonk bored carry clap confused cry cuddle dance facepalm feed handhold handshake happy highfive hug kabedon kill kiss lappillow laugh lurk nod nom nope nya pat peck poke pout punch run salute shake shocked shoot shrug sip slap sleep smile smug spin stare tableflip teehee think thumbsup tickle wag wave wink yawn yeet'.split(' ')
)

export const LIVE_CASES = Object.freeze({
  ping:        { args:[], expected:'text' },
  about:       { args:[], expected:'text' },
  calc:        { args:['2+2'], expected:'text' },
  joke:        { args:[], expected:'text' },
  uptime:      { args:[], expected:'text' },
  random:      { args:['red','|','blue'], expected:'text' },
  choose:      { args:['red','|','blue'], expected:'text' },
  fact:        { args:[], expected:'text', provider:true },
  define:      { args:['computer'], expected:'text', provider:true },
  translate:   { args:['french','Hello','world'], expected:'text', provider:true },
  weather:     { args:['Lagos'], expected:'text', provider:true },
  github:      { args:['facebook/react'], expected:'text', provider:true },
  wiki:        { args:['Nigeria'], expected:'imageOrText', provider:true },
  news:        { args:['technology'], expected:'text', provider:true },
  web:         { args:['OpenAI'], expected:'text', provider:true },
  image:       { args:['Saturn','1'], expected:'image', provider:true },
  quote:       { args:['This','is','a','NIGHT','test'], expected:'image' },
  wanted:      { args:[], expected:'image' },
  wasted:      { args:[], expected:'image' },
  roast:       { args:['NIGHT'], expected:'text' },
  caption:     { fixture:'image', args:['NIGHT','TEST'], expected:'image' },
  sq:          { fixture:'text', args:['dark'], expected:'image' },
  qr:          { args:['night-testall-qr'], expected:'image' },
  reactions:   { args:[], expected:'text' },
  emojimix:    { args:['😍','🔥'], expected:'sticker', provider:true },
  ssweb:       { args:['https://github.com'], expected:'image', provider:true },
  youtube:     { args:['--download','jNQXAC9IVRw','best','video'], expected:'video', provider:true },
  linkinfo:    { args:['https://example.com'], expected:'text', provider:true },

  sticker:     { fixture:'image', expected:'sticker' },
  toimg:       { fixture:'sticker', expected:'image' },
  togif:       { fixture:'animated-sticker', expected:'document' },
  tovideo:     { fixture:'animated-sticker', expected:'video' },
  bestframe:   { fixture:'video', args:['2'], expected:'image' },
  reverse:     { fixture:'video', expected:'video' },
  stabilize:   { fixture:'video', expected:'video' },
  aspectratio: { fixture:'video', args:['1:1','fit'], expected:'video' },
  extractaudio:{ fixture:'video', expected:'document' },
  denoise:     { fixture:'audio', expected:'audio' },
  clearaudio:  { fixture:'audio', args:['natural'], expected:'audio' },
  silencecut:  { fixture:'audio', args:['gentle'], expected:'audio' },
  sfx:         { fixture:'audio', args:['robot','65'], expected:'audio' },
  img2pdf:     { fixture:'image', expected:'document' },
  pdf2img:     { fixture:'pdf', args:['1'], expected:'image' },
  pdfsplit:    { fixture:'pdf', args:['1'], expected:'document' },
  pdfcompress: { fixture:'pdf', expected:'document' },
  pdfmerge:    { fixture:'pdf', expected:'document', multi:'merge' },
  scanqr:      { fixture:'qr', expected:'text', contains:'night-testall-qr' },
  stripmeta:   { fixture:'image', expected:'imageOrDocument' },
  getexif:     { fixture:'image', expected:'text' },
  upscale:     { fixture:'image', args:['2'], expected:'image' },
  removebg:    { fixture:'image', expected:'document' },
  stt:         { fixture:'speech', expected:'text', provider:true },
  vision:      { fixture:'image', args:['What is shown in this image?'], expected:'text', provider:true },
  tts:         { args:['NIGHT','WhatsApp','test'], expected:'audio', provider:true },
  tictactoe:   { expected:'image', menu:'tictactoe' },
  ...Object.fromEntries(LIVE_REACTION_NAMES.map(name=>[name,{expected:'sticker'}])),
})

// The list is intentionally explicit. Even if a new command is registered,
// it cannot inherit live permissions through a broad "safe-sounding" category.
export const LIVE_NAMES = Object.freeze(Object.keys(LIVE_CASES))
const BAD_RESPONSE = /(?:\b(?:could not|cannot|can't|failed|error|unknown|unavailable|not configured|not found|unable to|no results|no images found|usage:)\b|^(?:use \.|reply to |no (?:batch|audio|image|pdf)))/i

export function isTestOwner(ctx) { return ctx?.isSupremeOwner === true }
export function isGroupTest(ctx) {
  return Boolean(ctx?.groupKey || String(ctx?.message?.key?.remoteJid || '').endsWith('@g.us'))
}
export function authorizeTest(ctx, action, confirm) {
  if (!isTestOwner(ctx)) return 'Only the verified NIGHT supreme owner may run command tests.'
  const chat = String(ctx?.message?.key?.remoteJid || '')
  if (!chat || !ctx?.account?.sock?.sendMessage) return 'WhatsApp connection is unavailable.'
  if (isGroupTest(ctx) && action === 'live' && !confirm) {
    return 'Group live tests send many real media messages. Run *.testall live confirm* to authorize this group run. No participant/admin/account changes will be performed.'
  }
  return ''
}

export function sniffMedia(bytes) {
  if (!Buffer.isBuffer(bytes) || bytes.length < 12) return 'invalid'
  if (bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return 'png'
  if (bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return 'jpeg'
  if (bytes.toString('ascii',0,4) === 'RIFF' && bytes.toString('ascii',8,12) === 'WEBP') return 'webp'
  if (bytes.toString('ascii',0,4) === 'OggS') return 'ogg'
  if (bytes.toString('ascii',0,4) === 'GIF8') return 'gif'
  if (bytes.toString('ascii',0,5) === '%PDF-') return 'pdf'
  if (bytes.toString('ascii',4,8) === 'ftyp') return 'mp4'
  if (bytes.toString('ascii',0,3) === 'ID3' || (bytes[0] === 255 && (bytes[1] & 224) === 224)) return 'mp3'
  return 'unknown'
}

export function validateMediaPayload(content) {
  const kind = ['image','video','audio','document','sticker'].find(k => content?.[k])
  if (!kind) return {kind:'text',valid:typeof content?.text === 'string' && Boolean(content.text.trim())}
  const raw = content[kind]
  // URL/external media cannot be decoded here; don't claim local verification.
  if (!Buffer.isBuffer(raw)) return {kind,valid:false,reason:'output is not an inspectable Buffer'}
  const magic = sniffMedia(raw)
  const allowed = {
    image:['png','jpeg','webp'], video:['mp4'], audio:['ogg','mp3'],
    sticker:['webp'], document:['pdf','png','jpeg','gif','mp3','mp4','ogg','webp'],
  }
  return {kind,valid:raw.length>128 && allowed[kind].includes(magic),magic,size:raw.length,
    reason:allowed[kind].includes(magic)?'':'invalid '+kind+' signature ('+magic+')'}
}

// Verify the bytes actually decode before a command counts as successful.
// Magic bytes alone are insufficient: truncated MP4/Opus/PDF would otherwise pass.
// Several production media commands send {url:<temporary local file>}.
// Read only a bounded file under the OS temp directory, not arbitrary paths or
// remote URLs, then send the identical verified bytes to WhatsApp.
export async function materializeLocalOutput(content) {
  const kind=['image','video','audio','document','sticker'].find(k=>content?.[k])
  const file=kind && content[kind]
  if (!file || typeof file!=='object' || Buffer.isBuffer(file) || !file.url)
    return content
  if (typeof file.url!=='string' || !file.url.startsWith(tmpdir()+sep)) {
    throw new Error('Live test refuses non-temporary media URL output')
  }
  const resolved=await realpath(file.url)
  if(!resolved.startsWith(tmpdir()+sep)) throw new Error('Media output resolves outside the temporary directory')
  const info=await stat(resolved)
  if(!info.isFile() || !info.size || info.size>20*1024*1024) throw new Error('Media output exceeds live test limit')
  return {...content,[kind]:await readFile(resolved)}
}

export async function decodeOutput(content) {
  const checked=validateMediaPayload(content)
  if (!checked.valid || checked.kind === 'text') return checked
  const bytes=content[checked.kind]
  try {
    if (checked.magic === 'pdf') {
      const pdf=await PDFDocument.load(bytes)
      if (pdf.getPageCount()<1) throw new Error('PDF contains no pages')
    } else if (['png','jpeg','webp','gif'].includes(checked.magic)) {
      const img=await loadImage(bytes)
      if (img.width<16 || img.height<16) throw new Error('Decoded image has invalid dimensions')
      if (checked.kind==='sticker' && (img.width!==512 || img.height!==512))
        throw new Error('Sticker is not 512×512')
    } else if (['mp4','ogg','mp3'].includes(checked.magic)) {
      const dir=await mkdtemp(join(tmpdir(),'night-testall-probe-'))
      try {
        const ext=checked.magic==='mp4'?'mp4':checked.magic==='ogg'?'ogg':'mp3'
        const file=join(dir,'inspect.'+ext)
        await writeFile(file,bytes)
        const out=await runMediaProcess(process.env.FFPROBE_PATH||'ffprobe',
          ['-v','error','-show_entries','format=duration:stream=codec_type,width,height',
            '-of','json',file],{timeoutMs:14000})
        const probe=JSON.parse(out)
        const need=checked.kind==='video'?'video':'audio'
        if (!probe.streams?.some(stream=>stream.codec_type===need) && !(checked.kind==='document' &&
            probe.streams?.some(stream=>['audio','video'].includes(stream.codec_type)))) {
          throw new Error('No decodable '+need+' stream')
        }
        const duration=Number(probe.format?.duration||0)
        if (!(duration>0 && duration<360)) throw new Error('Invalid media duration')
      } finally {await rm(dir,{recursive:true,force:true})}
    }
  } catch(error) {
    return {...checked,valid:false,reason:'Decode failure: '+String(error?.message||error).slice(0,150)}
  }
  return checked
}

export function verdictForOutputs(outputs, expected, contains='') {
  const errors = outputs.filter(o => o.error)
  if (errors.length) return {status:'FAILED',detail:errors[0].error}
  const actual = outputs.filter(o => o.delivered)
  if (!actual.length) return {status:'FAILED',detail:'No WhatsApp messages accepted'}
  const candidates = actual.filter(o =>
    expected === 'imageOrDocument' ? ['image','document'].includes(o.kind) :
    expected === 'imageOrText' ? ['image','text'].includes(o.kind) : o.kind === expected)
  if (!candidates.length) {
    const explanation=actual.filter(o=>o.kind==='text').map(o=>o.text).filter(Boolean).join(' | ')
    return {status:'FAILED',detail:'Expected '+expected+'; got '+actual.map(o=>o.kind).join(', ')+
      (explanation?' — '+explanation.slice(0,200):'')}
  }
  const good = candidates.filter(o => o.valid)
  if (!good.length) return {status:'FAILED',detail:'Output media validation failed'}
  if (expected === 'text' || expected === 'imageOrText') {
    const goodText = good.filter(o=>o.kind==='text' && !BAD_RESPONSE.test(o.text.trim()))
    const goodImages = expected === 'imageOrText' ? good.filter(o=>o.kind==='image') : []
    if (!goodText.length && !goodImages.length) return {status:'FAILED',detail:'Command responded with an error or usage prompt: '+good[0].text.slice(0,200)}
    if (contains && !goodText.some(o=>o.text.includes(contains))) return {status:'FAILED',detail:'Output does not contain expected test content'}
  }
  return {status:'PASS',detail:good.length+' valid output(s) accepted by WhatsApp (recipient playback not verified)'}
}

export async function makeTestFixtures() {
  const dir = await mkdtemp(join(tmpdir(), 'night-testall-'))
  const canvas = createCanvas(480,320)
  const g = canvas.getContext('2d')
  g.fillStyle = '#2962a7'; g.fillRect(0,0,480,320)
  g.fillStyle = '#f8ca42'; g.fillRect(45,45,165,215)
  g.fillStyle = '#ffffff'; g.fillRect(250,85,172,160)
  g.fillStyle = '#151515'; g.font = '32px sans-serif'; g.fillText('NIGHT',258,173)
  const image = canvas.toBuffer('image/png')
  const qr = await QRCode.toBuffer('night-testall-qr', {type:'png',width:320,margin:3})
  const pdfDoc = await PDFDocument.create()
  const p1 = pdfDoc.addPage([320,430]); p1.drawText('NIGHT automated command test, page 1')
  const p2 = pdfDoc.addPage([320,430]); p2.drawText('NIGHT automated command test, page 2')
  const pdf = Buffer.from(await pdfDoc.save())
  const cache = { image, qr, pdf }
  return {
    async get(kind) {
      if (kind === 'text') return Buffer.from('NIGHT text quote test')
      if (cache[kind]) return cache[kind]
      const file = join(dir,kind === 'video' ? 'source.mp4' : 'source.ogg')
      const common = [ '-hide_banner','-loglevel','error','-y' ]
      if (kind === 'speech') {
        const { azureTextToSpeech } = await import('./azure-media.js')
        const sample = await azureTextToSpeech('Hello. This is a NIGHT command transcription test.')
        const source=join(dir,'source-speech.mp3')
        await writeFile(source,sample.buffer)
        await runMediaProcess(process.env.FFMPEG_PATH||'ffmpeg',
          [...common,'-i',source,'-ar','48000','-ac','1','-c:a','libopus',file],
          {timeoutMs:20000})
      } else if (kind === 'video') {
        await runMediaProcess(process.env.FFMPEG_PATH||'ffmpeg',
          [...common,'-f','lavfi','-i','testsrc2=size=320x180:rate=12:duration=7',
            '-f','lavfi','-i','sine=frequency=440:duration=7','-t','7',
            '-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p',
            '-c:a','aac','-b:a','48k','-shortest',file],{timeoutMs:35000})
      } else if (kind === 'audio') {
        await runMediaProcess(process.env.FFMPEG_PATH||'ffmpeg',
          [...common,'-f','lavfi','-i','sine=frequency=440:duration=3',
            '-af','volume=0.35','-ar','48000','-c:a','libopus',file],{timeoutMs:18000})
      } else if (kind === 'sticker') {
        cache.sticker = await mediaToSticker(image,{animated:false,packName:'NIGHT Test Lab'})
        return cache.sticker
      } else if (kind === 'animated-sticker') {
        cache['animated-sticker'] = await mediaToSticker(await this.get('video'),
          {animated:true,packName:'NIGHT Test Lab'})
        return cache['animated-sticker']
      } else throw new Error('Unrecognized media fixture')
      cache[kind] = await readFile(file)
      return cache[kind]
    },
    async close() {await rm(dir,{recursive:true,force:true})},
  }
}

export function fixtureMessage(kind, bytes) {
  if (kind === 'text') return {text:'NIGHT screenshot quote test'}
  const label = 'NIGHT TEST SOURCE • '+kind
  if (kind === 'image' || kind === 'qr')
    return {image:bytes,mimetype:'image/png',caption:label}
  if (kind === 'video') return {video:bytes,mimetype:'video/mp4',caption:label}
  if (kind === 'audio' || kind === 'speech') return {audio:bytes,mimetype:'audio/ogg; codecs=opus',ptt:true}
  if (kind === 'pdf') return {document:bytes,mimetype:'application/pdf',fileName:'night-test.pdf',caption:label}
  if (kind === 'sticker' || kind === 'animated-sticker') return {sticker:bytes}
  throw new Error('Unexpected fixture kind')
}

// Build a valid WhatsApp quoted-message shape from the actual message returned
// by sock.sendMessage. This exercises downloadMediaMessage, not just a fake
// Buffer injected into the business logic.
export function attachFixture(originalMessage, sent, chat, text) {
  if (!sent?.message || !sent?.key?.id) throw new Error('Fixture send did not return a WhatsApp message ID')
  return {
    ...originalMessage,
    message: { extendedTextMessage: { text,
      contextInfo: {
        stanzaId:sent.key.id,
        remoteJid:chat,
        ...(sent.key.participant?{participant:sent.key.participant}:{}),
        quotedMessage:sent.message,
      },
    }},
  }
}

function memoryStore() {
  const state = new Map()
  const key = (ns,id) => String(ns)+'\u0000'+String(id)
  return {
    get:(ns,id)=>state.get(key(ns,id)),
    set:(ns,id,value)=>state.set(key(ns,id),value),
    delete:(ns,id)=>state.delete(key(ns,id)),
  }
}

export async function runLiveCase({ original, command, spec, fixtures, fixtureSends, signal, onMessage, timeoutMs=TEST_TIMEOUT_MS }) {
  const chat = String(original.message?.key?.remoteJid || '')
  const sent = []
  const socket = original.account.sock
  if (!chat || !socket?.sendMessage) return {status:'FAILED',detail:'WhatsApp socket unavailable'}
  const share = memoryStore()
  const safeSend = async (jid, payload, options) => {
    if (signal?.aborted || stopped.signal.aborted) throw new Error('Audit cancelled')
    if (jid !== chat) throw new Error('Test attempted to send outside the authorized chat')
    const checkedPayload = await materializeLocalOutput(payload)
    const validation = await decodeOutput(checkedPayload)
    if (!validation.valid) throw new Error(validation.reason || 'Empty or unrecognized WhatsApp output')
    const answer = await socket.sendMessage(jid,checkedPayload,options)
    if (!answer?.key?.id) throw new Error('WhatsApp did not acknowledge the message')
    sent.push({delivered:true,kind:validation.kind,valid:validation.valid,
      text:String(checkedPayload?.text||'').slice(0,MAX_REPLY)})
    return answer
  }
  const account = {...original.account,sock:new Proxy(socket,{
    get(target,property) {
      if (property === 'sendMessage') return safeSend
      const item = Reflect.get(target,property,target)
      return typeof item === 'function' ? item.bind(target) : item
    },
  })}
  const base = {...original,account,shared:share,
    publicPrefix:original.publicPrefix||'.',
    userKey:'night-testall-isolated-owner',
    args:spec.args||[],
    rawArgs:(spec.args||[]).join(' '),
    reply:async text => safeSend(chat,{text:String(text)},{quoted:original.message}),
    sendImageUrl:async(url,caption='')=>{
      // Use the same public-only, SSRF-guarded downloader as ordinary commands;
      // capture the image through the audited socket instead of the original
      // context's callback (which bypasses our delivery instrumentation).
      const result=await downloadImageUrl(url)
      if (!Buffer.isBuffer(result?.buffer) || !result.buffer.length) {
        throw new Error('Public thumbnail returned no usable image.')
      }
      return safeSend(chat,{image:result.buffer,caption:String(caption||'')},
        {quoted:original.message})
    },
    sendImageDataUrl:async(dataUrl,caption='')=>{
      const match=String(dataUrl).match(/^data:image\/(png|jpe?g);base64,(.+)$/s)
      if(!match) throw new Error('QR generated an invalid image data URI')
      return safeSend(chat,{image:Buffer.from(match[2],'base64'),caption},{quoted:original.message})
    },
  }
  let fakeMessage = original.message
  const sourceFor = async(kind, fresh=false) => {
    if (!fresh && fixtureSends.has(kind)) return fixtureSends.get(kind)
    const bytes=await fixtures.get(kind)
    const source=await safeSend(chat,fixtureMessage(kind,bytes),{quoted:original.message})
    // Fixture sends are evidence of fixture upload only, NOT command output.
    sent.pop()
    if (!fresh) fixtureSends.set(kind,source)
    await sleep(CHAT_GAP_MS)
    return source
  }
  let workerTimer
  let timedOut = false
  const stopped = new AbortController()
  const abort = () => stopped.abort()
  signal?.addEventListener('abort',abort,{once:true})
  const execute = async() => {
    let media
    if (spec.fixture) media=await sourceFor(spec.fixture)
    fakeMessage = media
      ? attachFixture(original.message,media,chat,'.'+command.name+' '+(spec.args||[]).join(' '))
      : {...original.message,message:{conversation:'.'+command.name+' '+(spec.args||[]).join(' ')}}
    const run = async(args,message=fakeMessage) =>
      command.run({...base,args,rawArgs:args.join(' '),message})
    if (spec.multi === 'merge') {
      try {
        await run(['add'])
        const alternate=await sourceFor('pdf',true)
        await run(['add'],attachFixture(original.message,alternate,chat,'.pdfmerge add'))
        await run(['finish'],original.message)
      } finally {clearMediaSession({...base,message:original.message},'pdfmerge')}
    } else if (spec.menu === 'tictactoe') {
      const rows=[]
      const ui={bottomSheet:async options=>{rows.push(...(options.rows||[]));return true}}
      await command.run({...base,message:original.message,args:[],rawArgs:'',ui})
      if (!rows.some(row=>String(row.id||'').includes('~bot'))) throw new Error('Bot option absent from game menu')
      const levels=[]
      await command.run({...base,message:original.message,args:['~bot'],rawArgs:'~bot',
        ui:{bottomSheet:async options=>{levels.push(...(options.rows||[]));return true}}})
      const easy=levels.find(row=>String(row.id||'').includes('~botlevel'))
      if (!easy) throw new Error('Bot difficulty option absent from game menu')
      const args=String(easy.id).trim().split(/\s+/).slice(1)
      await run(args,original.message)
    } else await run(spec.args||[])
    return verdictForOutputs(sent,spec.expected,spec.contains)
  }
  const execPromise=execute()
  // Don't let an unresponsive integration block status/cancellation forever.
  const timeoutPromise=new Promise((_,reject)=>{
    workerTimer=setTimeout(()=>{timedOut=true;abort();reject(new Error('test timeout'))},timeoutMs)
  })
  const cancelPromise=new Promise((_,reject)=>{
    stopped.signal.addEventListener('abort',()=>reject(new Error(timedOut?'test timeout':'audit cancelled')),{once:true})
    if (stopped.signal.aborted) reject(new Error('audit cancelled'))
  })
  try {
    const result=await Promise.race([execPromise,timeoutPromise,cancelPromise])
    await onMessage?.(command.name,result)
    return result
  } catch(error) {
    const failure={status:signal?.aborted?'CANCELLED':'FAILED',
      detail:String(error?.message||error).slice(0,240)}
    await onMessage?.(command.name,failure)
    return failure
  } finally {
    clearTimeout(workerTimer)
    signal?.removeEventListener('abort',abort)
    // The timed-out command may still finish locally. Its socket proxy refuses
    // late sends, even while its own operation is unwinding.
    if (timedOut) stopped.abort()
  }
}

export function createLiveAuditManager({ load=()=>loadCommands(PUBLIC,{capabilityFromDirectory:true}),
  makeFixtures=makeTestFixtures, runCase=runLiveCase }={}) {
  let active=null
  let previous=null
  const status=()=>active
    ? '🌙 NIGHT live audit: '+active.done+'/'+active.total+' tests completed; '+active.failed+' failed. '+(active.name||'')
    : previous ? 'Last live audit: '+previous.passed+' passed; '+previous.failed.length+' failed; '+previous.skipped+' not live-tested.'
      : 'No NIGHT live test has run yet.'
  const busy=()=>Boolean(active)
  const cancel=()=>{if (!active) return false;active.controller.abort();return true}
  const start=(ctx,{failedOnly=false}={})=>{
    if(active) return false
    if(failedOnly && (!previous || !previous.failed.length)) return null
    const job={controller:new AbortController(),done:0,total:0,failed:0,name:''}
    active=job
    const chat=String(ctx.message.key.remoteJid)
    const send=async text=>{
      try {await ctx.account.sock.sendMessage(chat,{text:String(text)},{quoted:ctx.message})}
      catch(error) {console.warn('[NIGHT live audit] report send failed:',String(error?.message||error).slice(0,120))}
    }
    setImmediate(async()=>{
      let fixtures
      const results=new Map()
      let skipped=0
      try {
        const registry=await load()
        const cases=failedOnly ? previous.failed : LIVE_NAMES
        const eligible=cases.filter(name=>registry.commands.has(name) && registry.commands.get(name)?.name===name)
        skipped=registry.canonical.length-eligible.length
        job.total=eligible.length
        await send('🌙 *NIGHT LIVE COMMAND TEST*\n'+eligible.length+
          ' real command handlers queued • '+skipped+
          ' other registered public commands not scheduled for this live run.\n'+
          'Fixtures and results are sent to THIS chat. No moderation or account mutations. Use .testall cancel to stop.')
        fixtures=await makeFixtures()
        const fixtureSends=new Map()
        for (const name of eligible) {
          if (job.controller.signal.aborted) break
          job.name='.'+name
          const spec=LIVE_CASES[name]
          const command=registry.commands.get(name)
          const result=await runCase({original:ctx,command,spec,fixtures,fixtureSends,
            signal:job.controller.signal})
          results.set(name,result)
          job.done++
          if(result.status==='FAILED') job.failed++
          await send((result.status==='PASS'?'✅':result.status==='CANCELLED'?'🛑':'❌')+
            ' *.'+name+'* — '+result.status+'\n'+result.detail)
          await sleep(CHAT_GAP_MS)
        }
        if(job.controller.signal.aborted) {
          await send('🛑 NIGHT live audit stopped at '+job.done+'/'+job.total+'. Unfinished commands are NOT passed.')
        }
        const failed=[...results].filter(([,r])=>r.status==='FAILED').map(([name])=>name)
        const omitted=registry.canonical.map(row=>row.name).filter(name=>!eligible.includes(name))
      if (!failedOnly && omitted.length) {
        // Full visibility: unexercised commands must not disappear from results.
        const lines=omitted.map(name=>'⚪ .'+name)
        for(let i=0;i<lines.length;i+=38) {
          await send('*NOT LIVE-TESTED* ('+(i+1)+'–'+Math.min(i+38,lines.length)+' / '+lines.length+')\n'+
            lines.slice(i,i+38).join('\n'))
        }
      }
      const passed=[...results].filter(([,r])=>r.status==='PASS').length
        previous={failed,passed,skipped,results}
        await send('🌙 *NIGHT LIVE TEST REPORT*\n✅ '+passed+' WhatsApp operations passed\n'+
          '❌ '+failed.length+' failed\n⚪ '+skipped+' public commands not executed in this run\n'+
          'Tests performed: '+job.done+'/'+job.total+
          '\nFailed: '+(failed.length?failed.map(n=>'.'+n).join(', '):'None detected')+
          '\nUse *.testall failed* to retry failed live tests.'+
          '\nPassing means the server produced valid-format output and WhatsApp acknowledged send, NOT that the recipient played or viewed it.')
      } catch(error) {
        await send('❌ NIGHT live audit interrupted: '+String(error?.message||error).slice(0,250))
      } finally {
        await fixtures?.close().catch(()=>{})
        if(active===job) active=null
      }
    })
    return true
  }
  return {start,status,cancel,busy,last:()=>previous}
}
export const liveCommandAudit=createLiveAuditManager()
