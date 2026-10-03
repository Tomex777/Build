import { targetArg } from './_group.js'
export default {
  name:'kick',
  description:'Remove a member from the group.',
  usage:'.kick @user',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    try {
      const target = await ctx.groupParticipantAction?.(targetArg(ctx.args), 'remove')
      return ctx.reply('Removed *' + (target?.displayName || 'that member') + '*.')
    } catch (error) { return ctx.reply(error?.message || 'I could not remove that member.') }
  },
}
