export default {
  name:'unmuteuser',
  description:'Allow a previously muted member to tag you again.',
  usage:'.unmuteuser @user',
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const raw = String(ctx.args?.[0] || '').trim()
    try {
      const target = await ctx.setMentionMute?.(raw, false)
      return ctx.reply('Unmuted mentions from *' + (target?.displayName || 'that member') + '*.')
    } catch (error) { return ctx.reply(error?.message || 'I could not unmute that member.') }
  },
}
