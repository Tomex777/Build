export default {
  name: 'storage',
  aliases: ['rom'],
  description: 'Show disk-backed shared storage statistics.',
  async run(ctx) {
    const stats = ctx.storageStats()
    await ctx.reply([
      '💾 MSCC shared storage',
      `Messages on disk: ${stats.messages}`,
      `Bot profiles: ${stats.profiles}`,
      `Sticky routes: ${stats.routes}`,
      `Shared data items: ${stats.sharedItems}`,
    ].join('\n'))
  },
}
