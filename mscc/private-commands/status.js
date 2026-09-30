export default {
  name: 'status',
  description: 'Show MSCC account, index and control-feature status.',
  async run(ctx) { await ctx.reply(await ctx.statusText(false)) },
}
