function libraryLines(summary = {}) {
  const total = Number(summary.total || 0)
  if (!total) return ['*Library*', 'Empty']

  const parts = [
    ['Anime', summary.anime],
    ['Manga', summary.manga],
    ['Movies', summary.movie],
    ['TV', summary.tv],
  ].filter(([, count]) => Number(count || 0) > 0)

  return [
    '*Library*',
    String(total) + ' saved',
    parts.length
      ? parts.map(([label, count]) => label + ' ' + Number(count)).join(' · ')
      : '',
    Number(summary.watching || 0) > 0
      ? '🔔 Watching releases: ' + Number(summary.watching)
      : '',
  ].filter(Boolean)
}

function profileText(profile = {}) {
  return [
    '👤 *' + String(profile.displayName || 'WhatsApp user') + '*',
    profile.role ? 'Role: ' + profile.role : '',
    '',
    ...libraryLines(profile.library),
  ].filter((line, index, rows) => line !== '' || rows[index - 1] !== '').join('\n')
}

export default {
  name:'profile',
  aliases:['pf'],
  description:'Show your MSCC profile card or the profile of a mentioned/replied-to user.',
  usage:'.profile [@user]',
  help:'Use it alone for yourself, mention somebody, or reply to their message. The card shows only public/group-visible identity and MSCC Library totals.',
  async run(ctx) {
    const raw = String(ctx.args[0] || '').trim()
    const resolved = typeof ctx.resolveCommandTarget === 'function'
      ? await ctx.resolveCommandTarget(raw)
      : { phoneNumber:'', source:'' }

    let phoneNumber = String(resolved?.phoneNumber || '').trim()
    if (!phoneNumber && !raw) phoneNumber = String(ctx.userKey || '').trim()

    if (!phoneNumber) {
      return ctx.reply(
        'Use ' + (ctx.publicPrefix || '.') + 'profile, mention somebody, or reply to their message with ' +
        (ctx.publicPrefix || '.') + 'profile.'
      )
    }

    const profile = typeof ctx.getPublicUserProfile === 'function'
      ? await ctx.getPublicUserProfile(phoneNumber)
      : null
    if (!profile) return ctx.reply('I could not build that profile right now.')

    const text = profileText(profile)
    const chat = ctx.message?.key?.remoteJid
    const sock = ctx.account?.sock

    if (profile.photoUrl && chat && sock) {
      try {
        return await sock.sendMessage(chat, {
          image:{ url:profile.photoUrl },
          caption:text,
        }, { quoted:ctx.message })
      } catch {}
    }

    return ctx.reply(text)
  },
}

export { profileText }
