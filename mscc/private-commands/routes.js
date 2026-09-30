export default {
  name: 'routes',
  aliases: ['botroutes'],
  description: 'Show sticky group bot selections stored on disk.',
  usage: '.routes [group-jid]',
  async run(ctx) {
    const rows = ctx.groupRoutes(ctx.args[0] || '')
    if (!rows.length) return ctx.reply('No sticky group routes are stored yet.')
    await ctx.reply(['🧭 Group routes','',...rows.map(row => `${row.group_jid} • ${row.capability} → ${row.account_id}`)].join('\n'))
  },
}
