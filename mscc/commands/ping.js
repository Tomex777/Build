export default {
  name: 'ping',
  description: 'Check that MSCC is alive and show its current status.',
  ownerOnly: true,
  async run(ctx) {
    await ctx.reply(await ctx.statusText(true))
  },
}
