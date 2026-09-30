export default {
  name: 'restart',
  description: 'Gracefully restart the MSCC service under systemd.',
  usage: '.restart confirm',
  async run(ctx) {
    if (String(ctx.args[0] || '').toLowerCase() !== 'confirm') return ctx.reply('Usage: .restart confirm')
    await ctx.reply('♻️ Restarting MSCC...')
    await ctx.requestRestart()
  },
}
