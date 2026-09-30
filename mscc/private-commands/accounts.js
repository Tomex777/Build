export default {
  name: 'accounts',
  aliases: ['sessions'],
  description: 'Show every WhatsApp session and the permanent Account A control session.',
  async run(ctx) {
    const d = ctx.diagnostics()
    const rows = d.accounts.map(account => {
      const main = account.id === 'A' ? ' • MAIN' : ''
      const destination = account.id === d.destination ? ' • CC inbox' : ''
      return `\${account.displayName || account.id} [\${account.id}]: \${account.status} • \${account.numberMasked}\${main}\${destination}`
    })
    await ctx.reply(['📱 MSCC accounts','',...rows].join('\n'))
  },
}
