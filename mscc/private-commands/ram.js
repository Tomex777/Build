export default {
  name: 'ram',
  aliases: ['memory'],
  description: 'Show current Node.js memory usage.',
  async run(ctx) {
    const memory = process.memoryUsage()
    const mib = value => (value / 1048576).toFixed(1)
    await ctx.reply(\`MSCC memory\nRSS: \${mib(memory.rss)} MB\nHeap: \${mib(memory.heapUsed)} / \${mib(memory.heapTotal)} MB\`)
  },
}
