export default {
  name: 'ram',
  aliases: ['memory'],
  description: 'Show the current Node.js memory usage.',
  ownerOnly: true,
  async run(ctx) {
    const memory = process.memoryUsage()
    const mib = value => (value / 1048576).toFixed(1)
    await ctx.reply(
      `MSCC memory\nRSS: ${mib(memory.rss)} MB\nHeap: ${mib(memory.heapUsed)} / ${mib(memory.heapTotal)} MB`
    )
  },
}
