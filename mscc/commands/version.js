export default {
  name: 'version',
  aliases: ['ver'],
  description: 'Show the MSCC, Node.js and WhatsApp Web versions.',
  ownerOnly: true,
  async run(ctx) {
    const d = ctx.diagnostics()
    await ctx.reply([
      `MSCC v${d.version}`,
      `Node ${process.version}`,
      `WhatsApp Web: ${d.waVersion || 'not resolved yet'}`,
    ].join('\n'))
  },
}
