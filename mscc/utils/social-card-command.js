import { downloadImageUrl } from './remote-image.js'
import { contextInfo } from './whatsapp/messages.js'

function clean(value, max = 120) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

// ctx.args is tokenised for command routing. The original command body is
// needed to retain paragraph breaks, indentation and Unicode in the post.
export function socialCardText(ctx, prefixTokens = 0) {
  let raw = String(ctx.rawArgs ?? (ctx.args || []).join(' '))
  for (let i = 0; i < prefixTokens; i += 1) {
    raw = raw.replace(/^\s*\S+/u, '')
  }
  return raw.replace(/^\s+/u, '').trimEnd()
}



/**
 * Parse optional mock engagement counters from the start of the card body.
 * Text after these flags is preserved, including newlines and Unicode.
 * The counters affect only the image; there are no real social interactions.
 */
export function parseSocialEngagementOptions(ctx, prefixTokens = 0, { type = 'tweet', style = 'generic' } = {}) {
  const known = new Set(['likes', 'replies', 'reposts', 'comments', 'shares', 'views'])
  const allowed = type === 'tweet'
    ? { likes:'likeCount', replies:'replyCount', reposts:'repostCount' }
    : style === 'story'
      ? { views:'viewCount' }
      : { likes:'likeCount', comments:'commentCount', replies:'commentCount', shares:'shareCount', reposts:'shareCount' }
  const engagement = {}
  let remaining = socialCardText(ctx, prefixTokens)

  for (;;) {
    remaining = remaining.replace(/^\s+/u, '')
    const start = /^--([a-z]+)(?=[\s=]|$)/iu.exec(remaining)
    if (!start || !known.has(start[1].toLowerCase())) break

    const name = start[1].toLowerCase()
    if (!Object.hasOwn(allowed, name)) {
      return { text:'', engagement:{}, error:'--' + name + ' is not available for this card. Available: ' + Object.keys(allowed).map(v => '--' + v).join(', ') }
    }
    const match = /^--([a-z]+)(?:=([^\s]*)|[ \t]+([^\s]+))(?=\s|$)/iu.exec(remaining)
    if (!match || !String(match[2] ?? match[3] ?? '').trim()) {
      return { text:'', engagement:{}, error:'Provide a number for --' + name + ' (for example --' + name + '=1.2k).' }
    }

    const rawCount = match[2] ?? match[3]
    const parts = /^(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d{1,2}))?([kmb])?$/iu.exec(rawCount)
    const multiplier = { k:1_000, m:1_000_000, b:1_000_000_000 }
    const number = parts
      ? Number(parts[1].replace(/,/g, '') + (parts[2] ? '.' + parts[2] : '')) *
        (multiplier[(parts[3] || '').toLowerCase()] || 1)
      : NaN
    if (!Number.isFinite(number) || !Number.isSafeInteger(Math.round(number)) ||
        (!parts?.[3] && Boolean(parts?.[2])) || number < 0 || number > 999_999_999) {
      return { text:'', engagement:{}, error:'Invalid --' + name + ' count. Use 0 to 999,999,999 (e.g. 1500, 1.5k or 2m).' }
    }

    const field = allowed[name]
    if (Object.hasOwn(engagement, field)) {
      return { text:'', engagement:{}, error:'You supplied the ' + name + ' count more than once.' }
    }
    engagement[field] = Math.round(number)
    remaining = remaining.slice(match[0].length)
  }

  return { text:remaining.replace(/^\s+/u, '').trimEnd(), engagement, error:null }
}

/**
 * WhatsApp serializes @mentions as @<LID/PN digits>, while WhatsApp itself
 * paints the user's contact label over that raw token. Reproducing that raw
 * string in an image reveals an opaque identifier, not a human-readable name.
 *
 * Only rewrite numeric tokens backed by this message's mentionedJid metadata:
 * never interpret a LID's digits as a phone number, or replace unrelated text.
 */
export async function socialCardMentions(ctx, value) {
  let text = String(value || '')
  const mentions = contextInfo(ctx?.message?.message)?.mentionedJid
  if (!Array.isArray(mentions) || !text.includes('@')) return text

  const resolved = new Map()
  for (const rawJid of [...new Set(mentions.map(value => String(value || '').replace(/:\d+(?=@)/, '').trim()))]) {
    const match = /^(\d{7,20})@(lid|hosted\.lid|s\.whatsapp\.net)$/.exec(rawJid)
    if (!match) continue
    const rawMention = '@' + match[1]
    // One mention can appear multiple times in a caption.
    const token = new RegExp('(^|[^\\p{L}\\p{N}_])' + rawMention + '(?![\\p{L}\\p{N}_])', 'gu')
    if (!token.test(text)) continue
    let name = resolved.get(rawJid)
    if (name === undefined) {
      try {
        name = typeof ctx.resolveSocialMentionName === 'function'
          ? await ctx.resolveSocialMentionName(rawJid)
          : ''
      } catch {
        name = ''
      }
      // Empty/generic/digit-only values are not identifiable names.
      name = String(name || '').replace(/[\r\n\t]+/g, ' ').trim().replace(/^@/, '').slice(0, 70)
      if (/^(?:\d+|whatsapp user|unknown(?: user)?|member|user)$/i.test(name)) name = ''
      resolved.set(rawJid, name)
    }
    token.lastIndex = 0
    text = text.replace(token, (_whole, prefix) => prefix + '@' + (name || 'member'))
  }
  return text
}

export async function resolveSocialAuthor(ctx, args = []) {
  const parts = [...args].map(value => String(value || '').trim()).filter(Boolean)
  let profile = null
  let targetConsumed = false

  if (parts[0]?.startsWith('@') && typeof ctx.resolveCommandTarget === 'function') {
    const target = await ctx.resolveCommandTarget(parts[0])
    const phone = String(target?.phoneNumber || '').trim()
    if (phone && typeof ctx.getPublicUserProfile === 'function') {
      profile = await ctx.getPublicUserProfile(phone)
      if (profile) {
        parts.shift()
        targetConsumed = true
      }
    }
  }

  if (!profile && typeof ctx.getPublicUserProfile === 'function') {
    profile = await ctx.getPublicUserProfile(String(ctx.userKey || '').trim())
  }

  // A user's own WhatsApp pushName is more authoritative than an app alias,
  // saved contact name or generic fallback stored in a profile.
  const liveSenderName = !targetConsumed ? ctx.message?.pushName : ''
  const authorName = clean(
    liveSenderName ||
    profile?.displayName ||
    (targetConsumed ? 'WhatsApp user' : 'Night User'),
    60,
  ) || 'Night User'

  let avatarBuffer = null
  const photoUrl = String(profile?.photoUrl || '').trim()
  if (photoUrl) {
    try {
      const downloaded = await downloadImageUrl(photoUrl)
      if (downloaded?.buffer?.length) avatarBuffer = downloaded.buffer
    } catch {}
  }

  return {
    authorName,
    avatarBuffer,
    targetConsumed,
    remainingArgs:parts,
  }
}
