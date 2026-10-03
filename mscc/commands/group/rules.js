export default {
  name:'rules',
  description:'Show the current group rules.',
  usage:'.rules',
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const rules = String(ctx.groupPolicyGet?.()?.rulesText || '').trim()
    if (!rules) return ctx.reply('This group does not have Night rules set yet.')
    return ctx.reply('📜 *Group Rules*\n\n' + rules)
  },
}
