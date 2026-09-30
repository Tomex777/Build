export default {
  name: 'source',
  aliases: ['sources'],
  description: 'View or save your persistent source choice for a command folder.',
  usage: '.source <folder> [source|clear]',
  async run(ctx) {
    const capability = String(ctx.args[0] || '').trim().toLowerCase()
    if (!capability) return ctx.reply('Usage: .source <folder> [source|clear]')

    const sources = ctx.listSources(capability)
    if (!sources.length) return ctx.reply(`No ${capability} sources are installed yet.`)

    const requested = String(ctx.args[1] || '').trim().toLowerCase()
    const prefix = ctx.publicPrefix || '.'

    if (requested === 'clear' || requested === 'auto') {
      ctx.clearSourceDefault(capability)
      return ctx.reply(sources.length === 1
        ? `✅ Cleared the saved ${capability} source. ${sources[0].name} will be used automatically because it is the only source.`
        : `✅ Cleared your ${capability} default. The source picker will appear next time.`)
    }

    if (requested) {
      const source = ctx.setSourceDefault(capability, requested)
      return ctx.reply(`✅ ${source.name} is now your default ${capability} source. This choice is saved across groups and bot sessions.`)
    }

    const current = ctx.getSourceDefault(capability)
    if (sources.length === 1) {
      return ctx.reply(`${sources[0].name} is the only installed ${capability} source, so it is used automatically. No default is needed.`)
    }

    const rows = sources.map(source => ({
      title: current === source.id ? `${source.name} ✓` : source.name,
      description: current === source.id
        ? 'Current default'
        : (source.description || `Make ${source.name} your default`),
      id: `${prefix}source ${capability} ${source.id}`,
    }))

    return ctx.replyList({
      title: `${capability} sources`,
      text: current
        ? `Current default: ${sources.find(source => source.id === current)?.name || current}`
        : 'No default is saved yet.',
      buttonText: 'Choose default',
      footer: `Clear the default: ${prefix}source ${capability} clear`,
      rows,
    })
  },
}
