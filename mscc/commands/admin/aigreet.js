import { toggleValue } from './_group.js'
export default {
  name:'aigreet',
  description:'Let Night write a short personality-aware greeting for new members.',
  usage:'.aigreet <on|off>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const enabled = toggleValue(ctx.args?.[0])
    if (enabled === null) return ctx.reply('AI greet is *' + (ctx.groupPolicyGet?.()?.aiGreet ? 'ON' : 'OFF') + '*.')
    const policy = ctx.groupPolicySet?.({ aiGreet:enabled })
    return ctx.reply('AI greet is now *' + (policy?.aiGreet ? 'ON' : 'OFF') + '*.')
  },
}
