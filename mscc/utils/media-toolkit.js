import { Worker } from 'node:worker_threads'
import { writeFile, readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { downloadCommandMedia } from './media-conversion.js'
import { runFfmpeg } from './media-conversion.js'
import { withMediaFiles, checkedOutput } from './extended-media.js'

// A bounded, single-job queue keeps PDFs and FFmpeg away from WhatsApp's message path.
let active = false
const waiting = []
export async function withMediaToolJob(work) {
  if (active && waiting.length >= 2) throw new Error('Night is busy with media. Try again shortly.')
  return new Promise((resolve, reject) => {
    const start = () => {
      active = true
      Promise.resolve().then(work).then(resolve, reject).finally(() => {
        active = false
        waiting.shift()?.()
      })
    }
    if (active) waiting.push(start)
    else start()
  })
}

export function runMediaWorker(task, payload, timeoutMs = 90000) {
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./media-tool-worker.js', import.meta.url), {
      workerData: { task, payload },
      resourceLimits: { maxOldGenerationSizeMb: 192 },
    })
    let settled = false
    const finish = (err, value) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      worker.terminate().catch(() => {})
      if (err) reject(err)
      else resolve(value)
    }
    const timer = setTimeout(() => finish(new Error('Media processing timed out.')), timeoutMs)
    timer.unref?.()
    worker.once('message', message => {
      if (message?.ok) finish(null, message.result)
      else finish(new Error(String(message?.error || 'Media processing failed.')))
    })
    worker.once('error', error => finish(error))
    worker.once('exit', code => {
      if (!settled) finish(new Error('Media worker exited unexpectedly (' + code + ').'))
    })
  })
}

const LIMIT_FILE = 10 * 1024 * 1024
const LIMIT_SESSION = 18 * 1024 * 1024
const LIMIT_GLOBAL = 36 * 1024 * 1024
const SESSION_TTL = 15 * 60 * 1000
const sessions = new Map()

function cleanSessions() {
  const now = Date.now()
  for (const [key, entry] of sessions) if (now - entry.updated > SESSION_TTL) sessions.delete(key)
}
setInterval(cleanSessions, 60000).unref?.()

function sessionKey(ctx, type) {
  const chat = String(ctx.message?.key?.remoteJid || '')
  const user = String(ctx.userKey || ctx.message?.key?.participant || '')
  const account = String(ctx.account?.id || ctx.account?.name || ctx.botProfile?.id || '')
  if (!chat || !user) throw new Error('Cannot identify this conversation. Try again.')
  return [account, chat, user, type].join('|')
}
function validBuffer(value, label, cap = LIMIT_FILE) {
  const bytes = Buffer.from(value || [])
  if (!bytes.length || bytes.length > cap) throw new Error(label + ' must be between 1 byte and ' + Math.floor(cap / 1024 / 1024) + ' MB.')
  return bytes
}

export async function repliedPdf(ctx) {
  const source = await downloadCommandMedia(ctx, ['document'])
  if (!source) return null
  const bytes = validBuffer(source.buffer, 'PDF')
  if (bytes.subarray(0, 5).toString() !== '%PDF-') throw new Error('Reply to a genuine PDF document.')
  return { bytes, messageId: String(source.target?.key?.id || '') }
}

export async function repliedImage(ctx) {
  const source = await downloadCommandMedia(ctx, ['image', 'document'])
  if (!source) return null
  const mime = String(source.found?.media?.mimetype || '').toLowerCase()
  if (source.kind !== 'image' && !mime.startsWith('image/')) throw new Error('Reply to an image (PNG, JPEG or WebP).')
  return { bytes: validBuffer(source.buffer, 'Image', 8 * 1024 * 1024), messageId: String(source.target?.key?.id || '') }
}

export async function repliedAudio(ctx, { video = false } = {}) {
  const source = await downloadCommandMedia(ctx, video ? ['video', 'document'] : ['audio', 'document'])
  if (!source) return null
  const mime = String(source.found?.media?.mimetype || '').toLowerCase()
  if (video ? (source.kind !== 'video' && !mime.startsWith('video/'))
            : (source.kind !== 'audio' && !mime.startsWith('audio/'))) {
    throw new Error(video ? 'Reply to a video.' : 'Reply to a voice note or audio file.')
  }
  return {
    bytes: validBuffer(source.buffer, video ? 'Video' : 'Audio', 20 * 1024 * 1024),
    isVoice: !video && Boolean(source.found?.media?.ptt),
  }
}

export function addToMediaSession(ctx, type, file, maxItems = 8) {
  cleanSessions()
  const key = sessionKey(ctx, type)
  const old = sessions.get(key) || { items: [], updated: Date.now() }
  if (old.items.length >= maxItems) throw new Error('Maximum ' + maxItems + ' files per batch. Use .'+type+' finish.')
  const used = old.items.reduce((sum, item) => sum + item.bytes.length, 0)
  if (used + file.bytes.length > LIMIT_SESSION) throw new Error('Batch exceeds 18 MB. Finish this batch or clear it.')
  const total = [...sessions.values()].reduce((sum, session) =>
    sum + session.items.reduce((n, item) => n + item.bytes.length, 0), 0)
  if (total + file.bytes.length > LIMIT_GLOBAL) throw new Error('The shared media staging limit is busy. Try again shortly.')
  if (file.messageId && old.items.some(x => x.messageId === file.messageId)) {
    throw new Error('That message is already in this batch.')
  }
  old.items.push(file)
  old.updated = Date.now()
  sessions.set(key, old)
  return old.items.length
}
export function mediaSession(ctx, type) {
  cleanSessions()
  const entry = sessions.get(sessionKey(ctx, type))
  if (entry) entry.updated = Date.now()
  return entry?.items || []
}
export function clearMediaSession(ctx, type) {
  const key = sessionKey(ctx, type)
  const existed = sessions.has(key)
  sessions.delete(key)
  return existed
}

export async function sendMediaResults(ctx, results) {
  const chat = ctx.message?.key?.remoteJid
  const sock = ctx.account?.sock
  if (!chat || !sock) throw new Error('WhatsApp connection is unavailable.')
  for (const item of results) {
    const bytes = validBuffer(item.bytes, 'Output', 19 * 1024 * 1024)
    const base = item.asImage
      ? { image: bytes, mimetype: item.mime || 'image/png' }
      : { document: bytes, mimetype: item.mime || 'application/pdf', fileName: item.name || 'night-output.pdf' }
    await sock.sendMessage(chat, { ...base, caption: item.caption || '' }, { quoted: ctx.message })
  }
}

const AUDIO_FILTERS = {
  denoise: 'highpass=f=65,lowpass=f=12500,afftdn=nr=12:nf=-30',
  natural: 'highpass=f=65,lowpass=f=14000,equalizer=f=2800:t=q:w=1:g=1.5,acompressor=threshold=-24dB:ratio=1.8:attack=18:release=180,alimiter=limit=0.95',
  auto: 'highpass=f=75,lowpass=f=12500,afftdn=nr=9:nf=-32,equalizer=f=3000:t=q:w=1:g=2.5,acompressor=threshold=-24dB:ratio=2.5:attack=12:release=170:makeup=1.6,alimiter=limit=0.95',
  strong: 'highpass=f=95,lowpass=f=11000,afftdn=nr=18:nf=-35,equalizer=f=3100:t=q:w=1:g=3,acompressor=threshold=-27dB:ratio=3:attack=10:release=170:makeup=2,alimiter=limit=0.95',
}
export async function processAudio(bytes, { mode = 'auto', video = false } = {}) {
  if (!video && !AUDIO_FILTERS[mode]) throw new Error('Unknown audio mode.')
  return withMediaFiles('audio-tool', async dir => {
    const input = join(dir, 'input.bin')
    const out = join(dir, video ? 'extracted.mp3' : 'enhanced.ogg')
    await writeFile(input, bytes)
    if (video) {
      await runFfmpeg(['-t', '180', '-i', input, '-map', '0:a:0', '-vn',
        '-c:a', 'libmp3lame', '-q:a', '3', out], { timeoutMs: 90000 })
    } else {
      await runFfmpeg(['-t', '180', '-i', input, '-vn', '-af', AUDIO_FILTERS[mode],
        '-ar', '48000', '-ac', '1', '-c:a', 'libopus', '-b:a', '64k', out], { timeoutMs: 90000 })
    }
    return checkedOutput(out)
  })
}
export async function sendProcessedAudio(ctx, bytes, { video = false, isVoice = false, caption = '' } = {}) {
  const chat = ctx.message?.key?.remoteJid
  const sock = ctx.account?.sock
  if (!chat || !sock) throw new Error('WhatsApp connection is unavailable.')
  if (video) {
    return sock.sendMessage(chat, {
      document: Buffer.from(bytes), mimetype: 'audio/mpeg',
      fileName: 'night-extracted.mp3', caption: '🎵 Audio extracted (MP3).',
    }, { quoted: ctx.message })
  }
  return sock.sendMessage(chat, {
    audio: Buffer.from(bytes), mimetype: 'audio/ogg; codecs=opus', ptt: isVoice,
    ...(caption && !isVoice ? { caption } : {}),
  }, { quoted: ctx.message })
}
