import { toggleValue } from './_group.js'
export default {
  name:'antigroupmention',
  aliases:['antigroup'],
  description:'Block @all/@everyone/group-mention messages from non-admin members.',
  usage:'.antigroupmention <on|off>',
  adminOnly:true,
  async run(ctx) {
    if (!ctx.groupKey) return ctx.reply('This one is for groups.')
    const enabled = toggleValue(ctx.args?.[0])
    if (enabled === null) return ctx.reply('Anti-group-mention is *' + (ctx.groupPolicyGet?.()?.antiGroupMention ? 'ON' : 'OFF') + '*.')
    const policy = ctx.groupPolicySet?.({ antiGroupMention:enabled })
    return ctx.reply('Anti-group-mention is now *' + (policy?.antiGroupMention ? 'ON' : 'OFF') + '*.')
  },
}
