export default {
  name: 'replycc',
  description: 'Copy a view-once item when somebody replies to it.',
  ownerOnly: true,
  setting: {
    key: 'replyCc',
    label: 'Reply CC',
    description: 'Recover view-once media when a reply references it.',
  },
  async run(ctx) {
    const mode = String(ctx.args[0] || '').toLowerCase()
    if (!['on', 'off'].includes(mode)) {
      return ctx.reply(`Usage: .replycc on|off\nCurrent: ${ctx.settings.replyCc ? 'ON' : 'OFF'}`)
    }
    const enabled = mode === 'on'
    await ctx.setSetting('replyCc', enabled)
    await ctx.reply(`✅ replycc: ${enabled ? 'ON' : 'OFF'}`)
  },
}
