export default {
  name:'grouplink',
  aliases:['gclink'],
  description:'Show the current WhatsApp group invite link.',
  usage:'.grouplink',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    try { return ctx.reply(await ctx.groupInviteLink?.()) }
    catch (error) { return ctx.reply(error?.message || 'I could not get the group link.') }
  },
}
