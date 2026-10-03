export default {
  name:'request',
  description:'Send a feature, source, or broken-result request privately to the Night owner.',
  usage:'.request <what you need fixed or added>',
  async run(ctx) {
    const body = ctx.args.join(' ').trim()
    if (!body) return ctx.reply('Use .request <what you need fixed or added>.')

    const owner = ctx.ownerContact?.() || {}
    const phone = String(owner.phoneNumber || '').replace(/\D/g,'')
    if (!/^\d{7,15}$/.test(phone)) return ctx.reply('The Night owner contact is not configured.')

    const sender = String(ctx.message?.pushName || '').trim() || 'Night user'
    const source = await ctx.currentChatLabel?.()
    try {
      await ctx.account.sock.sendMessage(phone + '@s.whatsapp.net', {
        text:[
          '📨 *Night Request*',
          'From: ' + sender + (ctx.userKey ? ' (+' + ctx.userKey + ')' : ''),
          source ? 'Chat: ' + source : '',
          '',
          body,
        ].filter(Boolean).join('\n'),
      })
      return ctx.reply('Request sent to the Night owner.')
    } catch (error) {
      return ctx.reply(error?.message || 'I could not send that request.')
    }
  },
}
