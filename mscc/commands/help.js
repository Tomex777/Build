export default {
  name: 'help',
  aliases: ['commands', 'menu'],
  description: 'List installed commands or show details for one command.',
  ownerOnly: true,
  async run(ctx) {
    const commands = ctx.commandList()
      .filter(command => command.hidden !== true)
      .sort((a, b) => a.name.localeCompare(b.name))

    const wanted = String(ctx.args[0] || '').replace(/^\./, '').toLowerCase()
    if (wanted) {
      const command = commands.find(item =>
        item.name.toLowerCase() === wanted ||
        (item.aliases || []).some(alias => String(alias).toLowerCase() === wanted)
      )
      if (!command) return ctx.reply(`Unknown command: .${wanted}\nTry .help`)

      const rows = [
        `.${command.name}`,
        command.description || 'No description',
      ]
      if (command.aliases?.length) rows.push(`Aliases: ${command.aliases.map(alias => '.' + alias).join(', ')}`)
      if (command.usage) rows.push(`Usage: ${command.usage}`)
      if (command.setting?.label) rows.push(`Setting: ${command.setting.label}`)
      return ctx.reply(rows.join('\n'))
    }

    const rows = commands.map(command => `.${command.name} — ${command.description || 'No description'}`)
    await ctx.reply([
      'MSCC commands',
      '',
      ...rows,
      '',
      'Use .help <command> for details.',
    ].join('\n'))
  },
}
