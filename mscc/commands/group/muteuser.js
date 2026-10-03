export default {
  name:'muteuser',
  description:'Stop a specific group member from tagging you.',
  usage:'.muteuser @user',
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const raw = String(ctx.args?.[0] || '').trim()
    try {
      const target = await ctx.setMentionMute?.(raw, true)
      return ctx.reply('Muted mentions from *' + (target?.displayName || 'that member') + '*. If they tag you, Night will remove the message.')
    } catch (error) { return ctx.reply(error?.message || 'I could not mute that member.') }
  },
}
