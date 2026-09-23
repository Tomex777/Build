export default {
  name: 'help',
  aliases: ['commands', 'menu'],
  description: 'List the commands currently installed in MSCC.',
  ownerOnly: true,
  async run(ctx) {
    const rows = ctx.commandList()
      .filter(command => command.hidden !== true)
      .sort((a, b) => a.name.localeCompare(b.name))
      .map(command => `.${command.name} — ${command.description || 'No description'}`)
    await ctx.reply(['MSCC commands', '', ...rows].join('\n'))
  },
}
