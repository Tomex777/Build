import { downloadImageUrl } from './remote-image.js'

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
