export default {
  name: 'settings',
  aliases: ['config'],
  description: 'Show the current MSCC feature toggles.',
  ownerOnly: true,
  async run(ctx) {
    const onOff = value => value ? 'ON' : 'OFF'
    await ctx.reply([
      'MSCC settings',
      '',
      `Auto CC: ${onOff(ctx.settings.autoCc)}`,
      `Reply CC: ${onOff(ctx.settings.replyCc)}`,
      `Anti-delete: ${onOff(ctx.settings.antiDelete)}`,
    ].join('\n'))
  },
}
