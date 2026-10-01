const token = value => Buffer.from(JSON.stringify(value), 'utf8').toString('base64url')
const untoken = value => JSON.parse(Buffer.from(String(value || ''), 'base64url').toString('utf8'))

const titleOf = item => String(item?.title || item?.name || 'Untitled')
const idOf = item => String(item?.id ?? item?.slug ?? item?.packageName ?? item?.url ?? titleOf(item))

function normalizeApp(item, index = 0) {
  return {
    id:idOf(item),
    number:String(index + 1),
    title:titleOf(item),
    packageName:String(item?.packageName || item?.package || '').trim(),
    developer:String(item?.developer || item?.author || '').trim(),
    version:String(item?.version || '').trim(),
    description:String(item?.description || '').trim(),
    raw:item,
  }
}

function normalizeVersion(item, index = 0) {
  return {
    id:String(item?.id ?? item?.versionCode ?? item?.version ?? index + 1),
    number:String(index + 1),
    version:String(item?.version || item?.name || item?.versionName || `Version ${index + 1}`),
    versionCode:String(item?.versionCode || '').trim(),
    date:String(item?.date || item?.updated || '').trim(),
    raw:item,
  }
}

function normalizeVariant(item, index = 0) {
  return {
    id:String(item?.id ?? item?.url ?? index + 1),
    number:String(index + 1),
    title:String(item?.title || item?.name || item?.architecture || item?.arch || `Variant ${index + 1}`),
    architecture:String(item?.architecture || item?.arch || '').trim(),
    minSdk:String(item?.minSdk || item?.android || '').trim(),
    size:String(item?.size || '').trim(),
    raw:item,
  }
}

function parsePick(value, entries = []) {
  const n = Number(String(value || '').trim())
  if (!Number.isInteger(n) || n < 1 || n > entries.length) return null
  return entries[n - 1]
}

function outcomeError(ctx, outcome) {
  if (outcome?.status === 'no-sources') return ctx.reply('No Android app sources are installed yet.')
  if (outcome?.status === 'source-error') return ctx.reply(`${outcome.source?.name || 'The app source'} could not complete that request.`)
  if (outcome?.status === 'all-failed') return ctx.reply('All configured Android app sources failed for that request.')
  return ctx.reply('Could not complete that APK request.')
}

async function chooseSource(ctx, query) {
  const sources = ctx.listSources('android')
  const prefix = ctx.publicPrefix || '.'
  if (!sources.length) return outcomeError(ctx, { status:'no-sources' })
  return ctx.replyList({
    title:'Choose APK source',
    text:`Choose a source for “${query}”.`,
    buttonText:'Choose source',
    footer:`Save a default: ${prefix}source android <source>`,
    rows:sources.map(source => ({
      title:source.name,
      description:source.description || 'Use for this search',
      id:`${prefix}apk ~source ${source.id} ${token(query)}`,
    })),
  })
}

async function search(ctx, { query, sourceId = '' }) {
  if (!query) return ctx.reply('Usage: .apk <app name>')

  const outcome = await ctx.executeSource({
    capability:'android',
    explicitSource:sourceId,
    payload:{ action:'search', query },
  })
  if (outcome.status === 'choice-required') return chooseSource(ctx, query)
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result || {}
  const apps = (Array.isArray(result.items) ? result.items : result.item ? [result.item] : [])
    .map(normalizeApp)

  if (!apps.length) return ctx.reply(`No APK results found for “${query}”.`)

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'apk',
    stage:'app',
    sourceId:outcome.source.id,
    entries:apps,
    expiresAt:Date.now() + 30 * 60000,
  })

  return ctx.reply([
    `*APK results for “${query}”*`,
    '',
    ...apps.slice(0, 25).map(app => {
      const extra = [app.developer, app.packageName].filter(Boolean).join(' • ')
      return `${app.number}. ${app.title}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    'Reply with the app number.',
  ].join('\n'))
}

async function loadVersions(ctx, { sourceId, app }) {
  const outcome = await ctx.executeSource({
    capability:'android',
    explicitSource:sourceId,
    payload:{ action:'versions', itemId:app.id, item:app.raw || app },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)

  const result = outcome.result || {}
  const versions = (result.versions || result.items || []).map(normalizeVersion)
  if (!versions.length) {
    return loadVariants(ctx, {
      sourceId,
      app,
      version:{ id:'current', version:app.version || 'Current', raw:app.raw || app },
    })
  }

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'apk',
    stage:'version',
    sourceId,
    app,
    entries:versions,
    expiresAt:Date.now() + 30 * 60000,
  })

  return ctx.reply([
    `*${app.title} — versions*`,
    '',
    ...versions.slice(0, 25).map(version => {
      const extra = [version.versionCode && `code ${version.versionCode}`, version.date].filter(Boolean).join(' • ')
      return `${version.number}. ${version.version}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    'Reply with the version number.',
  ].join('\n'))
}

async function loadVariants(ctx, { sourceId, app, version }) {
  const outcome = await ctx.executeSource({
    capability:'android',
    explicitSource:sourceId,
    payload:{
      action:'variants',
      itemId:app.id,
      item:app.raw || app,
      version:version.raw || version,
      versionId:version.id,
    },
  })

  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
  const result = outcome.result || {}
  const variants = (result.variants || result.items || []).map(normalizeVariant)

  if (!variants.length) {
    return download(ctx, { sourceId, app, version, variant:null })
  }

  ctx.setCommandReplySession?.({
    kind:'number-selection',
    command:'apk',
    stage:'variant',
    sourceId,
    app,
    version,
    entries:variants,
    expiresAt:Date.now() + 30 * 60000,
  })

  return ctx.reply([
    `*${app.title} — ${version.version}*`,
    '',
    ...variants.slice(0, 25).map(variant => {
      const extra = [variant.architecture, variant.minSdk && `Android ${variant.minSdk}+`, variant.size].filter(Boolean).join(' • ')
      return `${variant.number}. ${variant.title}${extra ? ` — ${extra}` : ''}`
    }),
    '',
    'Reply with the variant number.',
  ].join('\n'))
}

async function download(ctx, { sourceId, app, version, variant }) {
  ctx.clearCommandReplySession?.()
  const outcome = await ctx.executeSource({
    capability:'android',
    explicitSource:sourceId,
    payload:{
      action:'download',
      itemId:app.id,
      item:app.raw || app,
      version:version?.raw || version || null,
      versionId:version?.id || '',
      variant:variant?.raw || variant || null,
      variantId:variant?.id || '',
      delivery:'document',
    },
  })
  if (outcome.status !== 'ok') return outcomeError(ctx, outcome)
  if (outcome.result?.delivered === true) return true
  if (typeof outcome.result === 'string') return ctx.reply(outcome.result)
  if (outcome.result?.text) return ctx.reply(String(outcome.result.text))
  return ctx.reply(`APK download started: ${app.title} ${version?.version || ''}.`.trim())
}

async function handleNumbers(ctx) {
  const session = ctx.getCommandReplySession?.()
  if (!session || session.command !== 'apk' || session.kind !== 'number-selection') {
    return ctx.reply('That APK selection expired. Run .apk again.')
  }

  const selected = parsePick(ctx.commandReplyInput, session.entries || [])
  if (!selected) return ctx.reply('Reply with one number from the APK list.')

  if (session.stage === 'app') {
    return loadVersions(ctx, { sourceId:session.sourceId, app:selected })
  }
  if (session.stage === 'version') {
    return loadVariants(ctx, { sourceId:session.sourceId, app:session.app, version:selected })
  }
  if (session.stage === 'variant') {
    return download(ctx, {
      sourceId:session.sourceId,
      app:session.app,
      version:session.version,
      variant:selected,
    })
  }
  return ctx.reply('That APK selection expired. Run .apk again.')
}

export async function runApkCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')
  if (first === '~numbers') return handleNumbers(ctx)

  if (first === '~source') {
    try {
      return search(ctx, {
        sourceId:String(args[1] || ''),
        query:untoken(args[2] || ''),
      })
    } catch {
      return ctx.reply('That APK source selection expired. Run .apk again.')
    }
  }

  return search(ctx, { query:args.join(' ').trim() })
}
