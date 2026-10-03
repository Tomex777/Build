import { toggleValue } from './_group.js'
export default {
  name:'antitag',
  description:'Delete member messages that tag users.',
  usage:'.antitag <on|off>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const enabled = toggleValue(ctx.args?.[0])
    if (enabled === null) return ctx.reply('Anti-tag is *' + (ctx.groupPolicyGet?.()?.antiTag ? 'ON' : 'OFF') + '*.\nUse .antitag on or .antitag off.')
    const policy = ctx.groupPolicySet?.({ antiTag:enabled })
    return ctx.reply('Anti-tag is now *' + (policy?.antiTag ? 'ON' : 'OFF') + '*.')
  },
}
