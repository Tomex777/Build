export default {
  name: 'destination',
  aliases: ['dest', 'inbox'],
  description: 'Show or change which linked account receives recovered media.',
  ownerOnly: true,
  async run(ctx) {
    const requested = String(ctx.args[0] || '').trim()
    const diagnostics = ctx.diagnostics()
    if (!requested) {
      const current = diagnostics.accounts.find(item => item.id === diagnostics.destination)
      await ctx.reply(
        `MSCC destination: ${current?.displayName || 'Account'} [${diagnostics.destination}]\nUsage: .destination <account-id>`
      )
      return
    }
    const account = diagnostics.accounts.find(item => item.id.toLowerCase() === requested.toLowerCase())
    if (!account?.enabled) {
      await ctx.reply(`Unknown or disabled account: ${requested}\nUse .accounts to list account IDs.`)
      return
    }
    await ctx.setDestination(account.id)
    await ctx.reply(`✅ Destination changed to ${account.displayName || 'Account'} [${account.id}]`)
  },
}
