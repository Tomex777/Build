// NIGHT anime reactions: use the EXISTING animated sticker converter, rounding and EXIF.
// GIFs are source material only; WhatsApp receives animated WebP stickers, never GIF messages.
import { contextInfo } from './utils/whatsapp/messages.js'
import { mediaToSticker } from './utils/media-conversion.js'
import { applyStickerMetadata, DEFAULT_STICKER_PACK_NAME } from './utils/sticker-metadata.js'
import { stickerPackNameForCommand } from './utils/sticker-metadata.js'
import { reactionProviders } from './utils/anime-reaction-providers.js'

export const REACTION_GROUPS = Object.freeze({
  'Affection': 'hug kiss cuddle pat peck blowkiss handhold lappillow kabedon carry',
  'Action': 'slap punch shoot bonk yeet akick kill bite poke tickle tableflip baka',
  'Emotions': 'angry blush bored confused cry happy laugh pout shocked shrug smile smug stare facepalm bleh teehee think nope',
  'Social': 'clap feed handshake highfive salute shake thumbsup wave wink nod nom nya wag',
  'Everyday': 'dance run sleep sip spin lurk yawn',
})
// `kick` belongs to the admin group-removal command. Never register it here.
export const REACTIONS = Object.freeze(Object.fromEntries(
  Object.values(REACTION_GROUPS).flatMap(group =>
    group.split(' ').map(name => [name, name === 'akick' ? 'kick' : name])
  )
))
export const REACTION_NAMES = Object.freeze(Object.keys(REACTIONS))
const USER_AGENT = 'NIGHT-Anime-Stickers (https://github.com/Tomex777/Build)'
const MAX_MEDIA_BYTES = 16 * 1024 * 1024
// Prepared animated WebP stickers are far cheaper to send than re-downloading
// and re-encoding a GIF on *every* reaction. Keep the cache bounded on the
// Azure worker (Node's heap limit is intentionally small), and scoped to the
// exact reaction + sticker pack so users' pack credits never leak.
const stickerCache = new Map()
const foreground = new Map()
const rawForeground = new Map()
const MAX_CACHE_BYTES = 10 * 1024 * 1024
const MAX_CACHE_KEYS = 24
const MAX_VARIANTS = 2
const MAX_VARIANT_BYTES = 900 * 1024
const CACHE_TTL_MS = 30 * 60 * 1000
const REFRESH_INTERVAL_MS = 90 * 1000
let backgroundBusy = false

function trimmed(entry, now = Date.now()) {
  return entry.variants.filter(row => now - row.at < CACHE_TTL_MS)
}
function cacheBytes() {
  let bytes = 0
  for (const entry of stickerCache.values()) for (const row of entry.variants) bytes += row.sticker.length
  return bytes
}
function enforceCacheLimits() {
  const now = Date.now()
  for (const [key, entry] of stickerCache) {
    entry.variants = trimmed(entry, now)
    if (!entry.variants.length) stickerCache.delete(key)
  }
  while (stickerCache.size > MAX_CACHE_KEYS || cacheBytes() > MAX_CACHE_BYTES) {
    stickerCache.delete(stickerCache.keys().next().value)
  }
}
function rememberSticker(cacheKey, sticker, sourceId) {
  if (!Buffer.isBuffer(sticker) || !sticker.length || sticker.length > MAX_VARIANT_BYTES) return
  let entry = stickerCache.get(cacheKey)
  if (!entry) entry = { variants:[],hits:0,nextRefresh:Date.now()+REFRESH_INTERVAL_MS }
  entry.variants = trimmed(entry).filter(item => item.sourceId !== sourceId)
  entry.variants.push({ sticker, sourceId, at:Date.now() })
  while (entry.variants.length > MAX_VARIANTS) entry.variants.shift()
  entry.nextRefresh = Date.now()+REFRESH_INTERVAL_MS
  stickerCache.delete(cacheKey)
  stickerCache.set(cacheKey,entry)
  enforceCacheLimits()
}
function cachedSticker(cacheKey) {
  const entry = stickerCache.get(cacheKey)
  if (!entry) return null
  entry.variants = trimmed(entry)
  if (!entry.variants.length) { stickerCache.delete(cacheKey); return null }
  const next = entry.variants[entry.hits++ % entry.variants.length]
  // A cache read updates LRU order, but never extends an expired source clip.
  stickerCache.delete(cacheKey)
  stickerCache.set(cacheKey,entry)
  return {sticker:next.sticker, sourceId:next.sourceId, entry}
}
export function animeReactionCacheStats() {
  enforceCacheLimits()
  return Object.freeze({
    keys:stickerCache.size,
    variants:[...stickerCache.values()].reduce((sum,entry)=>sum+entry.variants.length,0),
    bytes:cacheBytes(),
    maxBytes:MAX_CACHE_BYTES,
  })
}
const groupTag = /(^|\s)@(?:all|everyone|here)(?=\s|$|\W)/iu

function mentionedTargets(ctx) {
  const args = ctx.args || []
  const raw = args.join(' ')
  if (groupTag.test(raw)) throw new Error('Anime stickers cannot tag everyone. Mention one person instead.')
  const info = contextInfo(ctx.message?.message)
  const groupMentions = info?.groupMentions
  if (info?.isMentionAll === true || info?.mentionAll === true || (Array.isArray(groupMentions) && groupMentions.length)) {
    throw new Error('Group-wide mentions are not supported for anime stickers.')
  }
  const mentioned = [...new Set((info?.mentionedJid || []).map(String).filter(Boolean))]
  if (mentioned.length > 1) throw new Error('Mention only one person for an anime reaction.')
  if (raw.includes('@') && !mentioned.length) {
    throw new Error('I could not identify that person. Use a real WhatsApp mention or reply to their message.')
  }
  if (!ctx.groupKey) return []
  // Respect the actual WhatsApp mention/LID; NEVER derive a phone number from LID digits.
  if (mentioned.length) return mentioned
  const target = String(info?.stanzaId && info?.participant || '').trim()
  return target && /^\S+@(?:lid|s\.whatsapp\.net)$/.test(target) ? [target] : []
}

export function reactionCatalog(prefix = '.') {
  const lines = ['*NIGHT Anime Stickers*', 'Animated, rounded stickers · tag one person or reply to them.', '']
  for (const [section, commandNames] of Object.entries(REACTION_GROUPS)) {
    lines.push('*' + section + '*')
    lines.push(commandNames.split(' ').map(name => prefix + name).join(' · '))
    lines.push('')
  }
  lines.push('Existing ' + prefix + 'kick still removes members (admins only). Use ' + prefix + 'akick for the anime reaction.')
  return lines.join('\n')
}

async function downloadGif(url) {
  const response = await fetch(url, {
    headers: { 'User-Agent': USER_AGENT, Accept:'image/gif' },
    signal: AbortSignal.timeout(12000),
    redirect:'error',
  })
  if (!response.ok) throw new Error('Anime GIF download failed: HTTP ' + response.status)
  const type = String(response.headers.get('content-type') || '').toLowerCase()
  if (!type.includes('image/gif') && !type.includes('octet-stream')) throw new Error('Anime source returned non-GIF data')
  if (Number(response.headers.get('content-length') || 0) > MAX_MEDIA_BYTES) throw new Error('Anime GIF exceeds sticker limit')
  const reader = response.body?.getReader()
  if (!reader) throw new Error('Anime GIF has no response body')
  const chunks = []
  let total = 0
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      total += value.byteLength
      if (total > MAX_MEDIA_BYTES) throw new Error('Anime GIF exceeds sticker limit')
      chunks.push(Buffer.from(value))
    }
  } finally { reader.releaseLock() }
  const content = Buffer.concat(chunks, total)
  if (!['GIF87a', 'GIF89a'].includes(content.toString('ascii', 0, 6))) throw new Error('Anime source did not deliver a GIF')
  return content
}

// The original GIF-to-WebP conversion does not depend on the caller. Cache a
// *neutral* reaction WebP by category, then apply personalized pack metadata
// cheaply without FFmpeg. Different senders share the expensive work without
// accidentally receiving somebody else's pack name or publisher credits.
async function buildRawReaction(category, {limited = false} = {}) {
  const failures = []
  let providers = reactionProviders(category)
  if (limited) providers = providers.slice(0,2)
  for (const provider of providers) {
    let candidates
    try { candidates = await provider.resolve() }
    catch (error) {
      failures.push(provider.name + ': ' + String(error?.message || error).slice(0,140))
      continue
    }
    for (const url of candidates) {
      try {
        const gif = await downloadGif(url)
        const sticker = await mediaToSticker(gif, {
          animated:true, fastAnimated:true, packName:DEFAULT_STICKER_PACK_NAME,
        })
        if (sticker.length > MAX_VARIANT_BYTES) throw new Error('Converted sticker exceeds WhatsApp target size')
        rememberSticker('raw:'+category,sticker,url)
        return {sticker,sourceId:url}
      } catch (error) {
        failures.push(provider.name + ': ' + String(error?.message || error).slice(0,140))
      }
    }
  }
  throw new Error(failures.join(' | ').slice(0,600) || 'No available GIF sources')
}

async function prepareReactionSticker(category, packName, cacheKey, {limited=false,force=false} = {}) {
  let source = force ? null : cachedSticker('raw:'+category)
  if (!source) {
    let pending = rawForeground.get(category)
    if (!pending) {
      pending = buildRawReaction(category,{limited})
      rawForeground.set(category,pending)
      void pending.finally(() => {
        if (rawForeground.get(category)===pending) rawForeground.delete(category)
      }).catch(()=>{})
    }
    source = await pending
  }
  const sticker = await applyStickerMetadata(source.sticker,{packName})
  if (sticker.length > MAX_VARIANT_BYTES) throw new Error('Sticker metadata exceeds WhatsApp target size')
  rememberSticker(cacheKey,sticker,source.sourceId)
  return sticker
}

// Keep two varied clips ready for popular reactions without adding latency to
// the message being sent. Refresh is opportunistic, one at a time, and never
// performed during another foreground reaction conversion.
function maybeRefresh(cacheKey, category, packName, entry) {
  if (Date.now() < entry.nextRefresh || entry.hits < 2 || backgroundBusy) return
  entry.nextRefresh = Date.now() + REFRESH_INTERVAL_MS
  const timer = setTimeout(async () => {
    if (backgroundBusy || foreground.size) return
    backgroundBusy = true
    try {
      await prepareReactionSticker(category,packName,cacheKey,{limited:true,force:true})
    } catch {
      // Keep the previously verified animation; network providers are optional.
    } finally { backgroundBusy = false }
  }, 2500)
  timer.unref?.()
}

export async function sendReactionSticker(ctx, category) {
  const chat = ctx.message?.key?.remoteJid
  const sock = ctx.account?.sock
  if (!sock || !chat) return ctx.reply('WhatsApp is unavailable.')
  let mentions
  try { mentions = mentionedTargets(ctx) }
  catch (error) { return ctx.reply(error.message) }
  const packName = stickerPackNameForCommand(ctx)
  const cacheKey = category + '|' + packName
  const began = Date.now()
  let sticker, cached = false
  const ready = cachedSticker(cacheKey)
  if (ready) {
    sticker = ready.sticker
    cached = true
    maybeRefresh(cacheKey,category,packName,ready.entry)
  } else {
    let pending = foreground.get(cacheKey)
    if (!pending) {
      pending = prepareReactionSticker(category,packName,cacheKey)
      foreground.set(cacheKey,pending)
      // A failed provider must not strand all future requests for the same
      // reaction; delete only after the original pending task settles.
      void pending.finally(() => {
        if (foreground.get(cacheKey) === pending) foreground.delete(cacheKey)
      }).catch(()=>{})
    }
    try {
      sticker = await pending
    } catch (error) {
      console.warn('NIGHT anime reaction exhausted ('+category+'): '+String(error?.message||error).slice(0,650))
      return ctx.reply('NIGHT could not load that anime reaction from its available sticker sources right now. Try again shortly.')
    }
  }
  try {
    const delivered = await sock.sendMessage(chat, {
      sticker, ...(mentions.length ? {mentions} : {}),
    }, {quoted:ctx.message})
    const elapsed = Date.now()-began
    if (elapsed >= 2500) console.info('NIGHT reaction latency: '+category+' '+elapsed+'ms '+(cached?'cached':'prepared'))
    return delivered
  } catch (error) {
    console.warn('NIGHT reaction WhatsApp delivery failed ('+category+'): '+String(error?.message||error).slice(0,160))
    return ctx.reply('NIGHT prepared that anime sticker but WhatsApp could not send it. Try again shortly.')
  }
}

export function makeReactionCommand(name, category = REACTIONS[name]) {
  if (!category || !REACTION_NAMES.includes(name)) throw new Error('Unknown anime reaction: ' + name)
  return {
    name,
    description:'Send a rounded animated anime ' + category + ' sticker.',
    usage:'.' + name + ' [@person]',
    async run(ctx) { return sendReactionSticker(ctx, category) },
  }
}
