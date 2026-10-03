import { toggleValue } from './_group.js'

export default {
  name:'antispam',
  description:'Delete rapid or repeatedly duplicated spam from non-admin members.',
  usage:'.antispam <on|off>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const enabled = toggleValue(ctx.args?.[0])
    if (enabled === null) {
      return ctx.reply('Anti-spam is *' + (ctx.groupPolicyGet?.()?.antiSpam ? 'ON' : 'OFF') + '*.')
    }
    const policy = ctx.groupPolicySet?.({ antiSpam:enabled })
    return ctx.reply('Anti-spam is now *' + (policy?.antiSpam ? 'ON' : 'OFF') + '*.')
  },
}
