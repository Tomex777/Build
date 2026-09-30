function sourceFlag(args = []) {
  const clean = []
  let source = ''
  for (let i = 0; i < args.length; i++) {
    const value = String(args[i] || '')
    if (value === '--source' && args[i + 1]) {
      source = String(args[++i] || '').trim().toLowerCase()
      continue
    }
    if (value.startsWith('--source=')) {
      source = value.slice('--source='.length).trim().toLowerCase()
      continue
    }
    clean.push(value)
  }
  return { source, clean }
}

function renderSourceResult(result, source, botName) {
  if (typeof result === 'string') return result
  if (result?.text) return String(result.text)

  if (Array.isArray(result?.items)) {
    const lines = result.items.slice(0, 10).map((item, index) => {
      const title = item?.title || item?.name || `Result ${index + 1}`
      const extra = item?.description || item?.year || item?.status || ''
      return `${index + 1}. ${title}${extra ? ` — ${extra}` : ''}`
    })
    return [`*Results from ${botName || source.name}*`, ...lines].join('\n')
  }

  return `Completed with ${source.name}.`
}

export async function runSourceCommand(ctx, {
  capability,
  commandName,
  args = [],
  botName = '',
  action = 'search',
} = {}) {
  const parsed = sourceFlag(args)
  const query = parsed.clean.join(' ').trim()
  if (!query) return ctx.reply(`Usage: ${ctx.publicPrefix || '.'}${commandName} <query>`)

  const outcome = await ctx.executeSource({
    capability,
    explicitSource: parsed.source,
    payload: { action, query },
  })

  if (outcome.status === 'no-sources') {
    return ctx.reply(`No ${capability} sources are installed yet.`)
  }

  if (outcome.status === 'unknown-source') {
    const names = outcome.sources.map(source => source.id).join(', ')
    return ctx.reply(`Unknown ${capability} source "${parsed.source}". Available: ${names}`)
  }

  if (outcome.status === 'choice-required') {
    const prefix = ctx.publicPrefix || '.'
    const rows = outcome.sources.map(source => ({
      title: source.name,
      description: source.description || `Use ${source.name} for this request`,
      id: `${prefix}${commandName} ${query} --source ${source.id}`,
    }))
    return ctx.replyList({
      title: `Choose ${capability} source`,
      text: `More than one ${capability} source is available. Choose which one to use for this request.`,
      buttonText: 'Choose source',
      footer: `Save a default: ${prefix}source ${capability} <source>\nSee sources: ${prefix}source ${capability}`,
      rows,
    })
  }

  if (outcome.status === 'source-error') {
    return ctx.reply(`${outcome.source.name} could not complete that request. Choose another source with ${ctx.publicPrefix || '.'}source ${capability}.`)
  }

  if (outcome.status === 'all-failed') {
    return ctx.reply(`All configured ${capability} sources failed for that request.`)
  }

  const profileName = botName || ctx.botProfile?.displayName || 'MSCC'
  const body = renderSourceResult(outcome.result, outcome.source, profileName)
  const fallback = outcome.fallback
    ? `⚠️ Fallback: ${outcome.fallbackFrom?.name || 'your default source'} was unavailable, so ${outcome.source.name} was used.\n\n`
    : ''

  const defaultHint = parsed.source
    ? `\n\nMake ${outcome.source.name} your default: ${ctx.publicPrefix || '.'}source ${capability} ${outcome.source.id}`
    : ''

  return ctx.reply(`${fallback}${body}${defaultHint}`)
}
