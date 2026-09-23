export default {
  name: 'status',
  description: 'Show MSCC account, index and feature status.',
  ownerOnly: true,
  async run(ctx) {
    await ctx.reply(await ctx.statusText(false))
  },
}
