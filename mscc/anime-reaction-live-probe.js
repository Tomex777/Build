// Optional live validation; never part of production startup or mandatory general checks.
// Run from an internet-enabled CI runner: node anime-reaction-live-probe.js
import assert from 'node:assert/strict'
import { mediaToSticker } from './utils/media-conversion.js'
import { DEFAULT_STICKER_PUBLISHER } from './utils/sticker-metadata.js'

const CATEGORIES = 'angry baka bite bleh blowkiss blush bonk bored carry clap confused cry cuddle dance facepalm feed handhold handshake happy highfive hug kabedon kick kiss lappillow laugh lurk nod nom nope nya pat peck poke pout punch run salute shake shoot shocked shrug sip slap sleep smile smug spin stare tableflip teehee think thumbsup tickle wag wave wink yawn yeet'.split(' ')
const BASE = 'https://nekos.best/api/v2/'
const UA = 'NIGHT-Anime-Stickers (https://github.com/Tomex777/Build)'
const TIMEOUT = 15000
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))
const live = []

async function fetchLive(url, { json = false } = {}) {
  let error
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const response = await fetch(url, {
        headers: { 'User-Agent': UA, 'Accept': json ? 'application/json' : 'image/gif' },
        signal: AbortSignal.timeout(TIMEOUT),
      })
      if (!response.ok) throw new Error('HTTP ' + response.status)
      const contentType = response.headers.get('content-type') || ''
      if (json) {
        if (!contentType.includes('json')) throw new Error('Expected JSON, received ' + contentType)
        return await response.json()
      }
      if (!contentType.includes('image/gif') && !contentType.includes('octet-stream'))
        throw new Error('Expected GIF media, received ' + contentType)
      const body = Buffer.from(await response.arrayBuffer())
      if (body.length < 20 || body.length > 24 * 1024 * 1024) throw new Error('Invalid GIF size ' + body.length)
      if (!['GIF87a','GIF89a'].includes(body.subarray(0, 6).toString('ascii')))
        throw new Error('Unexpected GIF signature')
      return body
    } catch (e) {
      error = e
      if (attempt < 2) await sleep(300 * (attempt + 1))
    }
  }
  throw error
}

let endpoints
try {
  endpoints = await fetchLive(BASE + 'endpoints', {json:true})
  console.log('LIVE ENDPOINT LIST: ' + Object.keys(endpoints || {}).length + ' entries')
} catch (e) {
  console.error('LIVE ENDPOINTS FAILED: ' + e.message)
  process.exitCode = 1
}

for (const category of CATEGORIES) {
  if (!endpoints || endpoints[category]?.format !== 'gif') {
    live.push({ category, ok:false, reason:'Not confirmed as GIF category in live endpoint list' })
    continue
  }
  try {
    const result = await fetchLive(BASE + category + '?amount=1', {json:true})
    const url = result?.results?.[0]?.url
    if (!url || !new URL(url).hostname.endsWith('nekos.best')) throw new Error('Invalid media URL')
    const gif = await fetchLive(url)
    const entry = { category, ok:true, bytes:gif.length }
    if (['hug','slap','smug','kick'].includes(category)) {
      const sticker = await mediaToSticker(gif, {animated:true,packName:'NIGHT GIF validation'})
      assert.equal(sticker.toString('ascii',0,4),'RIFF')
      assert.equal(sticker.toString('ascii',8,12),'WEBP')
      assert.ok(sticker.includes(Buffer.from(DEFAULT_STICKER_PUBLISHER, 'utf8')), 'NIGHT sticker pack metadata')
      entry.stickerBytes = sticker.length
    }
    live.push(entry)
    console.log('PASS ' + category + ': ' + JSON.stringify(entry))
  } catch (error) {
    const fail = { category, ok:false, reason:String(error?.message||error).slice(0,180) }
    live.push(fail)
    console.log('FAIL ' + category + ': ' + fail.reason)
  }
}
const passed = live.filter(item=>item.ok).map(item=>item.category)
const failed = live.filter(item=>!item.ok)
console.log('LIVE REACTION SUMMARY ' + JSON.stringify({tested:live.length,passedCount:passed.length,failedCount:failed.length,passed,failed}))
if (!passed.length || !passed.includes('smug')) process.exitCode = 1
