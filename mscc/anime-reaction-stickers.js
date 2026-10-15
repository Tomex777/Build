// NIGHT anime reactions: use the EXISTING animated sticker converter, rounding and EXIF.
// GIFs are source material only; WhatsApp receives animated WebP stickers, never GIF messages.
import { randomInt } from 'node:crypto'
import { contextInfo } from './utils/whatsapp/messages.js'
import { mediaToSticker } from './utils/media-conversion.js'
import { stickerPackNameForCommand } from './utils/sticker-metadata.js'

export const REACTION_GROUPS = Object.freeze({
  'Affection': 'hug kiss cuddle pat peck blowkiss handhold lappillow kabedon carry',
  'Action': 'slap punch shoot bonk yeet akick bite poke tickle tableflip baka',
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
const API_ROOT = 'https://nekos.best/api/v2/'
const USER_AGENT = 'NIGHT-Anime-Stickers (https://github.com/Tomex777/Build)'
const MAX_MEDIA_BYTES = 16 * 1024 * 1024
const recent = new Map()
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

async function requestJson(category) {
  const response = await fetch(API_ROOT + encodeURIComponent(category) + '?amount=12', {
    headers: { 'User-Agent': USER_AGENT, Accept:'application/json' },
    signal: AbortSignal.timeout(12000),
  })
  const type = String(response.headers.get('content-type') || '')
  if (!response.ok || !type.includes('json')) throw new Error('Anime sticker source unavailable')
  const data = await response.json()
  const urls = (data?.results || []).map(row => String(row?.url || ''))
    .filter(url => {
      try {
        const parsed = new URL(url)
        return parsed.protocol === 'https:' &&
          (parsed.hostname === 'nekos.best' || parsed.hostname.endsWith('.nekos.best')) &&
          parsed.pathname.startsWith('/api/v2/' + category + '/') &&
          parsed.pathname.endsWith('.gif')
      } catch { return false }
    })
  if (!urls.length) throw new Error('No safe anime sticker source returned')
  const previous = recent.get(category) || []
  const unused = urls.filter(url => !previous.includes(url))
  const pool = unused.length ? unused : urls
  // Advance before downloads so concurrent commands are less likely to repeat.
  const selected = pool[randomInt(pool.length)]
  recent.set(category, [selected, ...previous.filter(url => url !== selected)].slice(0, 30))
  return [selected, ...urls.filter(url => url !== selected)].slice(0, 3)
}

async function downloadGif(url) {
  const response = await fetch(url, {
    headers: { 'User-Agent': USER_AGENT, Accept:'image/gif' },
    signal: AbortSignal.timeout(16000),
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

export async function sendReactionSticker(ctx, category) {
  const chat = ctx.message?.key?.remoteJid
  const sock = ctx.account?.sock
  if (!sock || !chat) return ctx.reply('WhatsApp is unavailable.')
  let mentions
  try { mentions = mentionedTargets(ctx) }
  catch (error) { return ctx.reply(error.message) }
  try {
    const candidates = await requestJson(category)
    let lastError
    for (const url of candidates) {
      try {
        const gif = await downloadGif(url)
        const sticker = await mediaToSticker(gif, {
          animated:true,
          packName:stickerPackNameForCommand(ctx),
        })
        if (sticker.length > 900 * 1024) throw new Error('Converted sticker exceeds WhatsApp target size')
        return sock.sendMessage(chat, { sticker, ...(mentions.length ? {mentions} : {}) }, {quoted:ctx.message})
      } catch (error) { lastError = error }
    }
    throw lastError || new Error('All downloaded stickers failed conversion')
  } catch (error) {
    console.warn('NIGHT anime sticker failed (' + category + '):', error?.message || error)
    return ctx.reply('That anime sticker is unavailable right now. Try again later.')
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
