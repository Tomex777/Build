export default {
  name:'hidetag',
  aliases:['tagall'],
  description:'Send a message that silently mentions every group member.',
  usage:'.hidetag [text]',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    try { return await ctx.groupHiddenTag?.(ctx.args.join(' ').trim()) }
    catch (error) { return ctx.reply(error?.message || 'I could not tag the group.') }
  },
}
