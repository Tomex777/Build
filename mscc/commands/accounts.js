export default {
  name: 'accounts',
  aliases: ['account', 'acc'],
  description: 'Show linked WhatsApp session state and the fixed Account A CC inbox.',
  ownerOnly: true,
  async run(ctx) {
    const d = ctx.diagnostics()
    const rows = d.accounts.map(account => {
      const destination = account.id === d.destination ? ' • CC inbox' : ''
      const configured = account.enabled ? account.numberMasked : 'not configured'
      const name = account.displayName || `Account ${account.id}`
      return `${name} [${account.id}]: ${account.status} • ${configured}${destination}`
    })
    await ctx.reply(['MSCC accounts', '', ...rows].join('\n'))
  },
}
