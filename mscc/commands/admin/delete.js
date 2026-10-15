export default {
  name: 'delete',
  aliases: ['del'],
  description: 'Delete a replied message, or delete recent group messages.',
  usage: '.delete (reply) | .delete <count>',
  adminOnly: true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')

    const raw = String(ctx.args[0] || '').trim()
    if (!raw) {
      const result = await ctx.deleteRepliedMessage?.()
      if (result?.deleted > 0) return // Success is silent, just like Nexia.
      return ctx.reply(result?.error || `🗑️ Reply to a message or use ${ctx.publicPrefix || '.'}delete <count>.`)
    }

    const count = /^\d+$/.test(raw) ? Number(raw) : NaN
    if (!Number.isSafeInteger(count) || count < 1 || count > 100) {
      return ctx.reply(`Usage: ${ctx.publicPrefix || '.'}delete <count> (1–100), or reply with ${ctx.publicPrefix || '.'}delete`)
    }

    if (typeof ctx.deleteRecentMessages !== 'function') return ctx.reply('Delete is not available right now.')
    const result = await ctx.deleteRecentMessages(count)
    if (result?.botAdmin === false) return ctx.reply('Night needs to be a group admin to delete messages.')
    if (!result?.deleted) return ctx.reply('I could not find any cached messages to delete.')
    // No success reply: count mode also erases the command after the messages.
  },
}
