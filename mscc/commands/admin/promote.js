import { targetArg } from './_group.js'

export default {
  name:'promote',
  description:'Promote a group member to admin.',
  usage:'.promote @user',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    try {
      const target = await ctx.groupParticipantAction?.(targetArg(ctx.args), 'promote')
      return ctx.replyWithMentions('✅ Promoted ' + target.mentionText + ' to admin.', [target.jid])
    } catch (error) { return ctx.reply(error?.message || 'I could not promote that member.') }
  },
}
