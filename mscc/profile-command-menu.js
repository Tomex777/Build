import {
  chooseProfileAsset,
  menuOpener,
  presentationFor,
  profileHeader,
  visibleCommandsForProfile,
} from './profile-presentation.js'

function usage(command, prefix) {
  const raw = String(command?.usage || `${prefix}${command?.name || ''}`).trim()
  return raw.startsWith('.') && prefix !== '.' ? `${prefix}${raw.slice(1)}` : raw
}

export function profileCommandMenuText(profileId, commands = [], prefix = '.') {
  const presentation = presentationFor(profileId)
  if (!presentation) return ''

  const visible = visibleCommandsForProfile(profileId, commands)
  const groups = new Map()
  for (const command of visible) {
    const capability = String(command?.capability || '').trim().toUpperCase()
    if (!capability) continue
    if (!groups.has(capability)) groups.set(capability, [])
    groups.get(capability).push(command)
  }

  const lines = [
    profileHeader(profileId),
    '',
    menuOpener(profileId),
  ]

  for (const capability of [...groups.keys()].sort()) {
    lines.push('', `${presentation.mark}  ${capability}`)
    for (const command of groups.get(capability).sort((a, b) => String(a.name).localeCompare(String(b.name)))) {
      lines.push(`│  ${usage(command, prefix)}`)
    }
    lines.push('╰────────────')
  }

  lines.push('', presentation.mark)
  return lines.filter(value => value !== undefined && value !== null).join('\n')
}

export async function sendProfileCommandMenu(ctx, profileId) {
  const text = profileCommandMenuText(profileId, ctx.commandList(), ctx.publicPrefix || '.')
  if (!text) return false

  const imagePath = await chooseProfileAsset(profileId, 'menu')
  if (imagePath && typeof ctx.sendImageFile === 'function') {
    try {
      await ctx.sendImageFile(imagePath, text)
      return true
    } catch {}
  }

  await ctx.reply(text)
  return true
}
