export default {
  name: 'accounts',
  aliases: ['account', 'acc'],
  description: 'Show Account A/B connection state and the current destination account.',
  ownerOnly: true,
  async run(ctx) {
    const d = ctx.diagnostics()
    const rows = d.accounts.map(account => {
      const destination = account.id === d.destination ? ' • destination' : ''
      const configured = account.enabled ? account.numberMasked : 'not configured'
      return `${account.id}: ${account.status} • ${configured}${destination}`
    })
    await ctx.reply(['MSCC accounts', '', ...rows].join('\n'))
  },
}
