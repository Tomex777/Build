export default {
  name:'unmute',
  aliases:['open'],
  description:'Open the group so members can send messages again.',
  usage:'.unmute',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    try {
      await ctx.groupSetAnnouncement?.(false)
      return ctx.reply('Group opened. Members can send messages again.')
    } catch (error) { return ctx.reply(error?.message || 'I could not open the group.') }
  },
}
