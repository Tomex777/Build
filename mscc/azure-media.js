import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { downloadCommandMedia, runFfmpeg } from './utils/media-conversion.js'

function envValue(name) {
  return String(process.env[name] || '').trim()
}

function xml(value) {
  return String(value || '')
    .replaceAll('&','&amp;')
    .replaceAll('<','&lt;')
    .replaceAll('>','&gt;')
    .replaceAll('"','&quot;')
    .replaceAll("'","&apos;")
}

function speechConfig() {
  const key = envValue('AZURE_SPEECH_KEY')
  const region = envValue('AZURE_SPEECH_REGION')
  if (!key || !region) throw new Error('Azure Speech is not configured on Night yet.')
  return { key, region }
}

function visionConfig() {
  const endpoint = envValue('AZURE_OPENAI_ENDPOINT').replace(/\/$/,'')
  const key = envValue('AZURE_OPENAI_API_KEY')
  const deployment = envValue('AZURE_OPENAI_VISION_DEPLOYMENT')
  const apiVersion = envValue('AZURE_OPENAI_API_VERSION') || '2024-10-21'
  if (!endpoint || !key || !deployment) throw new Error('Azure Vision is not configured on Night yet.')
  return { endpoint, key, deployment, apiVersion }
}

async function withTemp(label, work) {
  const dir = await mkdtemp(join(tmpdir(), 'night-' + label + '-'))
  try { return await work(dir) }
  finally { await rm(dir, { recursive:true, force:true }).catch(() => {}) }
}

export async function azureSpeechToText(ctx, {
  language = '',
} = {}) {
  const media = await downloadCommandMedia(ctx, ['audio','video'])
  if (!media) throw new Error('Reply to a voice note, audio file, or video with .stt.')

  const { key, region } = speechConfig()
  const wav = await withTemp('stt', async dir => {
    const input = join(dir, 'input.bin')
    const output = join(dir, 'speech.wav')
    await writeFile(input, media.buffer)
    await runFfmpeg([
      '-i', input,
      '-vn',
      '-ac', '1',
      '-ar', '16000',
      '-c:a', 'pcm_s16le',
      output,
    ], { timeoutMs:120000 })
    return readFile(output)
  })

  const lang = String(language || envValue('AZURE_STT_LANGUAGE') || 'en-US').trim()
  const url = 'https://' + region + '.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=' +
    encodeURIComponent(lang) + '&format=detailed'

  const response = await fetch(url, {
    method:'POST',
    headers:{
      'Ocp-Apim-Subscription-Key':key,
      'Content-Type':'audio/wav; codecs=audio/pcm; samplerate=16000',
      Accept:'application/json',
    },
    body:wav,
    signal:AbortSignal.timeout(120000),
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error('Azure STT returned HTTP ' + response.status + '.')
  const text = String(
    data?.NBest?.[0]?.Display ||
    data?.NBest?.[0]?.Lexical ||
    data?.DisplayText ||
    ''
  ).trim()
  if (!text) throw new Error('I could not hear any clear speech in that media.')
  return { text, language:lang, confidence:Number(data?.NBest?.[0]?.Confidence || 0) || 0 }
}

export async function azureTextToSpeech(text, {
  voice = '',
  language = '',
} = {}) {
  const body = String(text || '').trim()
  if (!body) throw new Error('Give me some text to speak.')

  const { key, region } = speechConfig()
  const selectedVoice = String(voice || envValue('AZURE_TTS_VOICE') || 'en-US-AvaMultilingualNeural').trim()
  const lang = String(language || envValue('AZURE_TTS_LANGUAGE') || 'en-US').trim()
  const ssml = '<speak version="1.0" xml:lang="' + xml(lang) + '"><voice name="' + xml(selectedVoice) + '">' +
    xml(body.slice(0,5000)) + '</voice></speak>'

  const response = await fetch('https://' + region + '.tts.speech.microsoft.com/cognitiveservices/v1', {
    method:'POST',
    headers:{
      'Ocp-Apim-Subscription-Key':key,
      'Content-Type':'application/ssml+xml',
      'X-Microsoft-OutputFormat':'audio-24khz-48kbitrate-mono-mp3',
      'User-Agent':'Night',
    },
    body:ssml,
    signal:AbortSignal.timeout(120000),
  })
  if (!response.ok) throw new Error('Azure TTS returned HTTP ' + response.status + '.')
  return {
    buffer:Buffer.from(await response.arrayBuffer()),
    mimetype:'audio/mpeg',
    voice:selectedVoice,
  }
}

export async function azureVisionAsk(imageBuffer, {
  question = 'Describe this image accurately.',
  mimeType = 'image/jpeg',
} = {}) {
  if (!Buffer.isBuffer(imageBuffer) || !imageBuffer.length) throw new Error('No image was supplied.')
  const { endpoint, key, deployment, apiVersion } = visionConfig()
  const dataUrl = 'data:' + String(mimeType || 'image/jpeg') + ';base64,' + imageBuffer.toString('base64')
  const url = endpoint + '/openai/deployments/' + encodeURIComponent(deployment) +
    '/chat/completions?api-version=' + encodeURIComponent(apiVersion)

  const response = await fetch(url, {
    method:'POST',
    headers:{
      'api-key':key,
      'content-type':'application/json',
    },
    body:JSON.stringify({
      messages:[{
        role:'user',
        content:[
          { type:'text', text:String(question || '').trim().slice(0,4000) || 'Describe this image accurately.' },
          { type:'image_url', image_url:{ url:dataUrl, detail:'auto' } },
        ],
      }],
      temperature:0.2,
      max_tokens:1200,
    }),
    signal:AbortSignal.timeout(120000),
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new Error('Azure Vision returned HTTP ' + response.status + '.')
  const text = String(payload?.choices?.[0]?.message?.content || '').trim()
  if (!text) throw new Error('Azure Vision returned no answer.')
  return text
}
