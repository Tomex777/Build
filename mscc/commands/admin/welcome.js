import { toggleValue } from './_group.js'
export default {
  name:'welcome',
  description:'Turn group welcome messages on or off.',
  usage:'.welcome <on|off>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const enabled = toggleValue(ctx.args?.[0])
    if (enabled === null) return ctx.reply('Welcome messages are *' + (ctx.groupPolicyGet?.()?.welcome ? 'ON' : 'OFF') + '*.')
    const policy = ctx.groupPolicySet?.({ welcome:enabled })
    return ctx.reply('Welcome messages are now *' + (policy?.welcome ? 'ON' : 'OFF') + '*.')
  },
}
