export default {
  name: 'ping',
  description: 'Check that the MSCC control process is alive.',
  async run(ctx) { await ctx.reply(await ctx.statusText(true)) },
}
