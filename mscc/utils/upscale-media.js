import { spawn } from 'node:child_process'
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { runFfmpeg } from './media-conversion.js'

const MAX_INPUT_BYTES = 10 * 1024 * 1024
const MAX_OUTPUT_BYTES = 20 * 1024 * 1024
const MAX_LOCAL_DIMENSION = 3072
const MAX_CLIPDROP_DIMENSION = 4096
const CLIPDROP_ENDPOINT = 'https://clipdrop-api.co/image-upscaling/v1/upscale'

export function upscaleFactor(raw = '') {
  const factor = Number(raw || 2)
  if (![2,4].includes(factor)) throw new Error('Choose 2x or 4x: .upscale 2 or .upscale 4')
  return factor
}

export function targetDimensions(width, height, factor, maxDimension = MAX_CLIPDROP_DIMENSION) {
  if (![width,height,factor].every(Number.isFinite) || width < 1 || height < 1) {
    throw new Error('Could not inspect the input image dimensions.')
  }
  const ratio = Math.min(factor, maxDimension / Math.max(width,height))
  if (ratio <= 1) throw new Error('This image is already too large to upscale with the selected backend.')
  return { width:Math.max(1,Math.floor(width * ratio)), height:Math.max(1,Math.floor(height * ratio)), actualScale:+ratio.toFixed(2) }
}

export function configuredClipdropKeys(env = process.env) {
  return [...new Set([env.CLIPDROP_API_KEY, ...(String(env.CLIPDROP_API_KEYS || '').split(/[\s,;]+/))]
    .map(value => String(value || '').trim()).filter(Boolean))]
}

function ffprobeDimensions(input) {
  const binary = String(process.env.FFPROBE_PATH || 'ffprobe').trim() || 'ffprobe'
  return new Promise((resolve,reject) => {
    const child = spawn(binary, ['-v','error','-select_streams','v:0','-show_entries','stream=width,height','-of','csv=p=0:s=x',input],{
      stdio:['ignore','pipe','ignore'],
    })
    let output = ''
    let settled = false
    const timer = setTimeout(() => { child.kill('SIGKILL') }, 12000)
    const finish = (error,value) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      if (error) reject(error)
      else resolve(value)
    }
    child.stdout.on('data',chunk => { output += chunk.toString().slice(0,100) })
    child.once('error',error => finish(error))
    child.once('close',code => {
      if (code !== 0) return finish(new Error('Cannot inspect image dimensions (ffprobe unavailable).'))
      const match = output.match(/^(\d+)x(\d+)/)
      return match
        ? finish(null,{width:Number(match[1]),height:Number(match[2])})
        : finish(new Error('Unsupported or damaged image.'))
    })
  })
}

function imageMime(buffer) {
  if (buffer.subarray(0,3).equals(Buffer.from([0xff,0xd8,0xff]))) return { mime:'image/jpeg', ext:'jpg' }
  if (buffer.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return { mime:'image/png', ext:'png' }
  if (buffer.subarray(0,4).toString('ascii') === 'RIFF' && buffer.subarray(8,12).toString('ascii') === 'WEBP') return { mime:'image/webp', ext:'webp' }
  throw new Error('Please use a JPG, PNG, or WebP image.')
}

async function clipdropUpscale(buffer, dimensions, factor, keys, { fetchImpl = globalThis.fetch } = {}) {
  const target = targetDimensions(dimensions.width,dimensions.height,factor)
  let lastError = 'AI provider unavailable'
  for (const key of keys.slice(0,5)) {
    const form = new FormData()
    const { mime, ext } = imageMime(buffer)
    form.append('image_file',new Blob([buffer],{type:mime}),'original.' + ext)
    form.append('target_width',String(target.width))
    form.append('target_height',String(target.height))
    try {
      const response = await fetchImpl(CLIPDROP_ENDPOINT,{
        method:'POST',
        headers:{ 'x-api-key':key },
        body:form,
        signal:AbortSignal.timeout(60000),
      })
      if (!response.ok) {
        lastError = 'AI provider returned ' + response.status
        if (![401,403,429].includes(response.status)) break
        continue
      }
      const mime = String(response.headers?.get?.('content-type') || '').toLowerCase()
      if (!mime.startsWith('image/')) { lastError='AI provider returned non-image data'; break }
      const size = Number(response.headers?.get?.('content-length') || 0)
      if (size > MAX_OUTPUT_BYTES) { lastError='AI output was too large'; break }
      const result = Buffer.from(await response.arrayBuffer())
      if (result.length && result.length <= MAX_OUTPUT_BYTES) {
        return { buffer:result, mimeType:mime.split(';')[0], label:'AI upscaled '+target.actualScale+'x (Clipdrop)' }
      }
      lastError = 'AI image output was empty or oversized'
      break
    } catch (error) { lastError = error?.name === 'TimeoutError' ? 'AI provider timed out' : 'AI provider unavailable' }
  }
  throw new Error(lastError)
}

export async function upscaleImage(buffer, { factor = 2, env = process.env, fetchImpl = globalThis.fetch } = {}) {
  if (!Buffer.isBuffer(buffer) || !buffer.length || buffer.length > MAX_INPUT_BYTES) {
    throw new Error('Please send an image smaller than 10 MB.')
  }
  const safeFactor = upscaleFactor(factor)
  imageMime(buffer)
  const directory = await mkdtemp(join(tmpdir(),'mscc-upscale-'))
  try {
    const input = join(directory,'input.bin')
    const output = join(directory,'upscaled.jpg')
    await writeFile(input,buffer)
    const dimensions = await ffprobeDimensions(input)
    const keys = configuredClipdropKeys(env)
    if (keys.length && typeof fetchImpl === 'function') {
      try {
        return await clipdropUpscale(buffer,dimensions,safeFactor,keys,{fetchImpl})
      } catch(error) {
        console.warn('[upscale] AI provider unavailable; using local resize:',error?.message || error)
      }
    }

    const target = targetDimensions(dimensions.width,dimensions.height,safeFactor,MAX_LOCAL_DIMENSION)
    const filter = 'scale=' + target.width + ':' + target.height + ':flags=lanczos,' +
      'unsharp=5:5:0.45:3:3:0.2,format=yuvj420p'
    await runFfmpeg(['-i',input,'-frames:v','1','-vf',filter,'-q:v','3',output],{timeoutMs:45000})
    const image = await readFile(output)
    if (!image.length || image.length > MAX_OUTPUT_BYTES) throw new Error('The upscaled result exceeded the image size limit.')
    return { buffer:image, mimeType:'image/jpeg', label:'HD resized '+target.actualScale+'x (not AI enhancement)' }
  } finally {
    await rm(directory,{recursive:true,force:true}).catch(()=>{})
  }
}
