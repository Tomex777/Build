export default {
  name: 'reloadsettings',
  aliases: ['reloadconfig'],
  description: 'Reload persisted MSCC settings from disk.',
  async run(ctx) {
    await ctx.reloadSettings()
    await ctx.reply('♻️ MSCC settings reloaded.')
  },
}
