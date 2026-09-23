export default {
  name: 'antidelete',
  aliases: ['ad'],
  description: 'Mirror messages that are deleted for everyone when they are still indexed.',
  ownerOnly: true,
  setting: {
    key: 'antiDelete',
    label: 'Anti-delete',
    description: 'Recover indexed messages when they are revoked.',
  },
  async run(ctx) {
    const mode = String(ctx.args[0] || '').toLowerCase()
    if (!['on', 'off'].includes(mode)) {
      return ctx.reply(`Usage: .antidelete on|off\nCurrent: ${ctx.settings.antiDelete ? 'ON' : 'OFF'}`)
    }
    const enabled = mode === 'on'
    await ctx.setSetting('antiDelete', enabled)
    await ctx.reply(`✅ antidelete: ${enabled ? 'ON' : 'OFF'}`)
  },
}
