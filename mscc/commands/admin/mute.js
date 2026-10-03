export default {
  name:'mute',
  aliases:['close'],
  description:'Close the group so only admins can send messages.',
  usage:'.mute',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    try {
      await ctx.groupSetAnnouncement?.(true)
      return ctx.reply('Group closed. Only admins can send messages now.')
    } catch (error) { return ctx.reply(error?.message || 'I could not close the group.') }
  },
}
