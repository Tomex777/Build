import { toggleValue } from './_group.js'
export default {
  name:'goodbye',
  description:'Turn group goodbye messages on or off.',
  usage:'.goodbye <on|off>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const enabled = toggleValue(ctx.args?.[0])
    if (enabled === null) return ctx.reply('Goodbye messages are *' + (ctx.groupPolicyGet?.()?.goodbye ? 'ON' : 'OFF') + '*.')
    const policy = ctx.groupPolicySet?.({ goodbye:enabled })
    return ctx.reply('Goodbye messages are now *' + (policy?.goodbye ? 'ON' : 'OFF') + '*.')
  },
}
