function normalizedUsage(command, prefix) {
  const raw = String(command.usage || `${prefix}${command.name}`).trim()
  if (raw.startsWith('.') && prefix !== '.') return `${prefix}${raw.slice(1)}`
  return raw
}

export default {
  name: 'help',
  aliases: ['commands'],
  description: 'List public commands or show help for one command.',
  usage: '.help [command]',
  async run(ctx) {
    const prefix = ctx.publicPrefix || '.'
    const requested = String(ctx.args[0] || '').trim().toLowerCase()
    const commands = ctx.commandList()

    if (requested) {
      const command = commands.find(item =>
        String(item.name || '').toLowerCase() === requested ||
        (item.aliases || []).some(alias => String(alias).toLowerCase() === requested)
      )
      if (!command) return ctx.reply(`Unknown command: ${prefix}${requested}`)

      const lines = [`*${prefix}${command.name}*`]
      if (command.description) lines.push(String(command.description))
      lines.push(`Usage: ${normalizedUsage(command, prefix)}`)
      const aliases = (command.aliases || []).map(alias => String(alias).trim()).filter(Boolean)
      if (aliases.length) lines.push(`Aliases: ${aliases.map(alias => `${prefix}${alias}`).join(', ')}`)
      if (command.help) lines.push('', String(command.help))
      return ctx.reply(lines.join('\n'))
    }

    const groups = new Map()
    for (const command of commands) {
      const capability = String(command.capability || 'general').trim().toLowerCase() || 'general'
      if (!groups.has(capability)) groups.set(capability, [])
      groups.get(capability).push(command)
    }

    const lines = ['*Commands*']
    for (const capability of [...groups.keys()].sort()) {
      const names = groups.get(capability)
        .sort((a, b) => String(a.name).localeCompare(String(b.name)))
        .map(command => `${prefix}${command.name}`)
      lines.push(`*${capability}*: ${names.join(', ')}`)
    }
    lines.push('', `Use ${prefix}<command> help for details.`)
    return ctx.reply(lines.join('\n'))
  },
}
