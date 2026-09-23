export default {
  name: 'destination',
  aliases: ['dest', 'inbox'],
  description: 'Show or change which linked account receives recovered media.',
  ownerOnly: true,
  async run(ctx) {
    const requested = String(ctx.args[0] || '').trim().toUpperCase()
    if (!requested) {
      await ctx.reply(`MSCC destination: Account ${ctx.diagnostics().destination}\nUsage: .destination A|B`)
      return
    }
    if (!['A', 'B'].includes(requested)) {
      await ctx.reply('Usage: .destination A|B')
      return
    }
    const account = ctx.diagnostics().accounts.find(item => item.id === requested)
    if (!account?.enabled) {
      await ctx.reply(`Account ${requested} is not configured.`)
      return
    }
    await ctx.setDestination(requested)
    await ctx.reply(`✅ Destination changed to Account ${requested}`)
  },
}
