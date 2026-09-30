export default {
  name: 'sources',
  description: 'List installed sources for one command folder or all source-backed folders.',
  usage: '.sources [folder]',
  async run(ctx) {
    const requested = String(ctx.args[0] || '').trim().toLowerCase()
    const capabilities = requested
      ? [requested]
      : [...new Set(ctx.commandList()
          .map(command => command.capability)
          .filter(Boolean))]
          .filter(capability => ctx.listSources(capability).length)
          .sort()

    if (!capabilities.length) return ctx.reply('No sources are installed yet.')

    const blocks = []
    for (const capability of capabilities) {
      const sources = ctx.listSources(capability)
      if (!sources.length) {
        if (requested) return ctx.reply(`No ${capability} sources are installed yet.`)
        continue
      }
      const mode = ctx.sourceMode(capability)
      const current = mode === 'user-choice' ? ctx.getSourceDefault(capability) : ''
      blocks.push(`*${capability}* — ${mode === 'managed' ? 'automatic' : 'user-selectable'}`)
      for (const source of sources) {
        const tags = []
        if (mode === 'managed') tags.push(source.primary ? 'primary' : 'fallback')
        if (current === source.id) tags.push('default')
        blocks.push(`• ${source.name}${tags.length ? ` (${tags.join(', ')})` : ''}`)
      }
      if (mode === 'user-choice') blocks.push(`Choose default: ${ctx.publicPrefix || '.'}source ${capability} <source>`)
      blocks.push('')
    }

    return ctx.reply(blocks.join('\n').trim())
  },
}
