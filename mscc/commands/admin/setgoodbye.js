export default {
  name:'setgoodbye',
  description:'Set the group goodbye message. Supports {user}, {group}, and {count}.',
  usage:'.setgoodbye <message>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const text = ctx.args.join(' ').trim()
    if (!text) return ctx.reply('Use .setgoodbye <message>. You can include {user}, {group}, and {count}.')
    ctx.groupPolicySet?.({ goodbyeText:text })
    return ctx.reply('Goodbye message updated.')
  },
}
