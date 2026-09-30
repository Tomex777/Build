export default {
  name: 'ram',
  aliases: ['memory'],
  description: 'Show current shared-process memory use and bounded runtime caches.',
  async run(ctx) {
    const memory = process.memoryUsage()
    const mib = value => (value / 1048576).toFixed(1)
    const d = ctx.diagnostics()
    await ctx.reply([
      'MSCC memory',
      `RSS: ${mib(memory.rss)} MB`,
      `Heap: ${mib(memory.heapUsed)} / ${mib(memory.heapTotal)} MB`,
      `Connected sessions: ${d.accounts.filter(account => account.connected).length}/${d.accounts.length}`,
      'Message retention: disk-backed',
    ].join('\n'))
  },
}
