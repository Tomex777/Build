export default {
  name: 'aidm',
  aliases: ['directai'],
  description: 'Allow or block smart AI conversations in direct messages.',
  usage: '.aidm on|off',
  setting: {
    key:'aiDirectMessages',
    default:false,
    label:'AI direct messages',
    description:'Allow Josiah smart AI to respond in direct messages. Group mentions/replies are unaffected.',
  },
  async run(ctx) {
    const mode = String(ctx.args[0] || '').toLowerCase()
    if (!['on','off'].includes(mode)) {
      return ctx.reply(`Usage: .aidm on|off\nCurrent: ${ctx.settings.aiDirectMessages === true ? 'ON' : 'OFF'}`)
    }

    const enabled = mode === 'on'
    await ctx.setSetting('aiDirectMessages', enabled)
    await ctx.reply(`🧠 AI direct messages: ${enabled ? 'ON' : 'OFF'}`)
  },
}
