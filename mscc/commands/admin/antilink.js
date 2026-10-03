import { toggleValue } from './_group.js'
export default {
  name:'antilink',
  description:'Delete links sent by non-admin members.',
  usage:'.antilink <on|off>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const enabled = toggleValue(ctx.args?.[0])
    if (enabled === null) return ctx.reply('Anti-link is *' + (ctx.groupPolicyGet?.()?.antiLink ? 'ON' : 'OFF') + '*.\nUse .antilink on or .antilink off.')
    const policy = ctx.groupPolicySet?.({ antiLink:enabled })
    return ctx.reply('Anti-link is now *' + (policy?.antiLink ? 'ON' : 'OFF') + '*.')
  },
}
