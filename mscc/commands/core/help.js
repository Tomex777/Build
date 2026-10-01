import {
  chooseProfileAsset,
  menuOpener,
  presentationFor,
  profileHeader,
  visibleCommandsForProfile,
} from '../../profile-presentation.js'

function normalizedUsage(command, prefix) {
  const raw = String(command.usage || `${prefix}${command.name}`).trim()
  if (raw.startsWith('.') && prefix !== '.') return `${prefix}${raw.slice(1)}`
  return raw
}

function formatUptime(totalSeconds) {
  const seconds = Math.max(0, Math.floor(Number(totalSeconds) || 0))
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days) return `${days}d ${hours}h`
  if (hours) return `${hours}h ${minutes}m`
  return `${minutes}m`
}

function detailText(profileId, command, prefix) {
  const presentation = presentationFor(profileId)
  if (!presentation) {
    const lines = [`*${prefix}${command.name}*`]
    if (command.description) lines.push(String(command.description))
    lines.push(`Usage: ${normalizedUsage(command, prefix)}`)
    const aliases = (command.aliases || []).map(alias => String(alias).trim()).filter(Boolean)
    if (aliases.length) lines.push(`Aliases: ${aliases.map(alias => `${prefix}${alias}`).join(', ')}`)
    if (command.help) lines.push('', String(command.help))
    return lines.join('\n')
  }

  const aliases = (command.aliases || []).map(alias => String(alias).trim()).filter(Boolean)
  const lines = [
    `${presentation.mark}  ${String(command.name || '').toUpperCase()}`,
    '',
  ]
  if (command.description) lines.push(String(command.description), '')
  lines.push('Usage', normalizedUsage(command, prefix))
  if (aliases.length) lines.push('', 'Also', ...aliases.map(alias => `${prefix}${alias}`))
  if (command.help) lines.push('', String(command.help))
  lines.push('', presentation.mark)
  return lines.join('\n')
}

function menuText(profileId, commands, prefix) {
  const presentation = presentationFor(profileId)
  if (!presentation) {
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
    lines.push('', `Use ${prefix}help <command> for details.`)
    return lines.join('\n')
  }

  const visible = visibleCommandsForProfile(profileId, commands)
  const groups = new Map()
  for (const command of visible) {
    const capability = String(command.capability || 'general').trim().toUpperCase() || 'GENERAL'
    if (!groups.has(capability)) groups.set(capability, [])
    groups.get(capability).push(command)
  }

  const opener = menuOpener(profileId)
  const lines = [
    profileHeader(profileId),
    '',
    opener,
    '',
    `${visible.length} commands · ${formatUptime(process.uptime())} online`,
  ]

  for (const capability of [...groups.keys()].sort()) {
    lines.push('', `${presentation.mark}  ${capability}`)
    const names = groups.get(capability)
      .sort((a, b) => String(a.name).localeCompare(String(b.name)))
      .map(command => `│  ${prefix}${command.name}`)
    lines.push(...names, '╰────────────')
  }

  lines.push('', presentation.mark, `_Use ${prefix}help <command> for details._`)
  return lines.join('\n')
}

export default {
  name: 'help',
  aliases: ['commands', 'menu'],
  description: 'List public commands or show help for one command.',
  usage: '.help [command]',
  async run(ctx) {
    const prefix = ctx.publicPrefix || '.'
    const requested = String(ctx.args[0] || '').trim().toLowerCase()
    const commands = ctx.commandList()
    const profileId = String(ctx.botProfile?.id || '').trim().toLowerCase()

    if (requested) {
      const command = commands.find(item =>
        String(item.name || '').toLowerCase() === requested ||
        (item.aliases || []).some(alias => String(alias).toLowerCase() === requested)
      )
      if (!command) return ctx.reply(`Unknown command: ${prefix}${requested}`)
      return ctx.reply(detailText(profileId, command, prefix))
    }

    const text = menuText(profileId, commands, prefix)
    const imagePath = await chooseProfileAsset(profileId, 'menu')
    if (imagePath && typeof ctx.sendImageFile === 'function') {
      try {
        return await ctx.sendImageFile(imagePath, text)
      } catch {}
    }
    return ctx.reply(text)
  },
}
