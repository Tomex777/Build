export default {
  name: 'settings',
  aliases: ['config'],
  description: 'Show the current command-driven MSCC feature toggles.',
  ownerOnly: true,
  async run(ctx) {
    const onOff = value => value ? 'ON' : 'OFF'
    const rows = ctx.commandList()
      .filter(command => command.setting?.key)
      .sort((a, b) => String(a.setting.label || a.name).localeCompare(String(b.setting.label || b.name)))
      .map(command => {
        const key = command.setting.key
        return `${command.setting.label || command.name}: ${onOff(ctx.settings[key])}  (.${command.name})`
      })

    await ctx.reply(['MSCC settings', '', ...rows].join('\n'))
  },
}
