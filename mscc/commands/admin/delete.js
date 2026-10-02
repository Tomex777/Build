export default {
  name: 'delete',
  aliases: ['del'],
  description: 'Silently delete the last N group messages.',
  usage: '.delete <count>',
  adminOnly: true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')

    const raw = String(ctx.args[0] || '').trim()
    const count = Number.parseInt(raw, 10)
    if (!Number.isFinite(count) || count < 1) {
      return ctx.reply(`Usage: ${ctx.publicPrefix || '.'}delete <count>`)
    }

    if (typeof ctx.deleteRecentMessages !== 'function') return
    await ctx.deleteRecentMessages(count)
    // Deliberately no success reply: the requested messages and this command
    // itself disappear, leaving no extra bot chatter behind.
  },
}
