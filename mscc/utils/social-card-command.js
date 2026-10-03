import { downloadImageUrl } from './remote-image.js'

function clean(value, max = 120) {
  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, max)
}

export async function resolveSocialAuthor(ctx, args = []) {
  const parts = [...args].map(value => String(value || '').trim()).filter(Boolean)
  let profile = null

  if (parts[0]?.startsWith('@') && typeof ctx.resolveCommandTarget === 'function') {
    const target = await ctx.resolveCommandTarget(parts[0])
    const phone = String(target?.phoneNumber || '').trim()
    if (phone && typeof ctx.getPublicUserProfile === 'function') {
      profile = await ctx.getPublicUserProfile(phone)
      parts.shift()
    }
  }

  if (!profile && typeof ctx.getPublicUserProfile === 'function') {
    profile = await ctx.getPublicUserProfile(String(ctx.userKey || '').trim())
  }

  const authorName = clean(
    profile?.displayName ||
    ctx.message?.pushName ||
    'Night User',
    48,
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
    remainingArgs:parts,
  }
}
