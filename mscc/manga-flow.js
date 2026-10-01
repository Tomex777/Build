import { counterpartInstantRows } from './media-relations.js'
import { runSourceCommand } from './source-flow.js'

async function resolveMangaIdentity(ctx, query, explicitId = 0) {
  if (explicitId && typeof ctx.resolveAniListMedia === 'function') {
    return ctx.resolveAniListMedia(explicitId, 'MANGA')
  }
  if (!query || typeof ctx.resolveAniListTitles !== 'function') return null
  const found = await ctx.resolveAniListTitles(query, 'MANGA')
  const id = found?.matches?.[0]?.id
  if (!id || typeof ctx.resolveAniListMedia !== 'function') return found?.matches?.[0] || null
  return ctx.resolveAniListMedia(id, 'MANGA')
}

async function showAnimeRelations(ctx, media) {
  if (!media?.id) return false
  const rows = counterpartInstantRows(media, {
    fromType:'MANGA',
    prefix:ctx.publicPrefix || '.',
    max:3,
  })
  if (!rows.length) return false

  await ctx.replyList({
    title:media.title || 'Related anime',
    text:'Anime adaptation',
    buttonText:'Open anime',
    rows:rows.map(({ mediaId, mediaType, relationType, ...row }) => row),
  })
  return true
}

export async function runMangaCommand(ctx, { args = [] } = {}) {
  const first = String(args[0] || '')
  let query = args.join(' ').trim()
  let media = null

  if (first === '~anilist') {
    media = await resolveMangaIdentity(ctx, '', Number(args[1]))
    if (!media?.id) return ctx.reply('I could not resolve that manga anymore. Run the manga search again. ✦')
    query = media.title || media.aliases?.[0] || ''
  } else if (query) {
    media = await resolveMangaIdentity(ctx, query)
  }

  const result = await runSourceCommand(ctx, {
    capability:'manga',
    commandName:'manga',
    args:query ? query.split(/\s+/) : [],
    botName:ctx.sourceBrand?.('manga') || 'Nami',
    action:'search',
  })

  if (media?.id) await showAnimeRelations(ctx, media)
  return result
}
