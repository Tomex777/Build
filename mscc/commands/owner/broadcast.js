export default {
  name:'broadcast',
  aliases:['bc'],
  description:'Broadcast one message once to every unique group Night is currently in.',
  usage:'.broadcast <text>',
  ownerOnly:true,
  async run(ctx) {
    const text = ctx.args.join(' ').trim()
    if (!text) return ctx.reply('Use .broadcast <text>.')
    try {
      const result = await ctx.broadcastNight?.(text)
      return ctx.reply([
        '📣 *Broadcast complete*',
        'Sent: ' + Number(result?.sent || 0),
        'Failed: ' + Number(result?.failed || 0),
        'Unique groups found: ' + Number(result?.total || 0),
      ].join('\n'))
    } catch (error) {
      return ctx.reply(error?.message || 'Broadcast failed.')
    }
  },
}
