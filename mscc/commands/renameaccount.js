export default {
  name: 'renameaccount',
  aliases: ['renameacc', 'accountname'],
  description: 'Change the friendly name of a WhatsApp session.',
  usage: '.renameaccount <account-id> <friendly name>',
  ownerOnly: true,
  async run(ctx) {
    const id = String(ctx.args[0] || '').trim()
    const displayName = ctx.args.slice(1).join(' ').trim()
    if (!id || !displayName) {
      return ctx.reply('Usage: .renameaccount <account-id> <friendly name>')
    }
    const result = await ctx.renameAccount(id, displayName)
    await ctx.reply(`✅ ${result.account} is now “${result.displayName}”.`)
  },
}
