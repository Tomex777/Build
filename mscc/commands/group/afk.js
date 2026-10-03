export default {
  name:'afk',
  description:'Mark yourself away in this group. Josia tracks mentions and replies until you return.',
  usage:'.afk [reason|off]',
  async run(ctx) {
    if (!ctx.groupKey || !ctx.account?.sock) return ctx.reply('This one is for groups.')

    const reason = ctx.args.join(' ').trim()
    if (['off','back','clear'].includes(reason.toLowerCase())) {
      const prior = ctx.afkClear?.()
      return ctx.reply(prior ? 'AFK cleared.' : 'You were not marked AFK.')
    }

    const state = ctx.afkSet?.(reason)
    const phone = String(ctx.userKey || '').replace(/\D/g,'')
    const jid = ctx.message?.key?.participant || (phone ? phone + '@s.whatsapp.net' : '')
    const text = [
      '◇ *Josia*',
      phone ? '@' + phone + ' is AFK now.' : 'You are AFK now.',
      reason ? 'Reason: ' + reason : '',
      'I’ll keep track of people who mention you or reply to your messages.',
    ].filter(Boolean).join('\n')

    return ctx.account.sock.sendMessage(ctx.groupKey, {
      text,
      mentions:jid ? [jid] : [],
    }, { quoted:ctx.message })
  },
}
