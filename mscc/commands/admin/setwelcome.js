export default {
  name:'setwelcome',
  description:'Set the group welcome message. Supports {user}, {group}, and {count}.',
  usage:'.setwelcome <message>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const text = ctx.args.join(' ').trim()
    if (!text) return ctx.reply('Use .setwelcome <message>. You can include {user}, {group}, and {count}.')
    ctx.groupPolicySet?.({ welcomeText:text })
    return ctx.reply('Welcome message updated.')
  },
}
