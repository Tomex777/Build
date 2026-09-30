export default {
  name: 'resetroutes',
  description: 'Clear sticky bot selections so eligible groups are recalculated.',
  usage: '.resetroutes [group-jid|all]',
  async run(ctx) {
    const raw = String(ctx.args[0] || '').trim()
    const count = ctx.resetGroupRoutes(raw && raw.toLowerCase() !== 'all' ? raw : '')
    await ctx.reply(`✅ Cleared ${count} stored group route${count === 1 ? '' : 's'}.`)
  },
}
