import { targetArg } from './_group.js'
export default {
  name:'demote',
  description:'Remove group-admin status from a member.',
  usage:'.demote @user',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    try {
      const target = await ctx.groupParticipantAction?.(targetArg(ctx.args), 'demote')
      return ctx.reply('Demoted *' + (target?.displayName || 'that member') + '*.')
    } catch (error) { return ctx.reply(error?.message || 'I could not demote that member.') }
  },
}
