const TYPE_ALIASES = new Map([
  ['anime','anime'],
  ['manga','manga'],
  ['movie','movie'],
  ['movies','movie'],
  ['film','movie'],
  ['tv','tv'],
  ['series','tv'],
  ['show','tv'],
  ['shows','tv'],
])

const TYPE_LABELS = {
  anime:'Anime',
  manga:'Manga',
  movie:'Movies',
  tv:'TV Series',
}

const COMMANDS = {
  anime:'anime',
  manga:'manga',
  movie:'movie',
  tv:'tv',
}

export function normalizeLibraryType(value) {
  return TYPE_ALIASES.get(String(value || '').trim().toLowerCase()) || ''
}

export function libraryTypeLabel(value) {
  return TYPE_LABELS[normalizeLibraryType(value)] || 'Library'
}

export function canonicalLibraryIdentity(type, media = {}) {
  const kind = normalizeLibraryType(type)
  if (!kind) return null
  const animeLike = kind === 'anime' || kind === 'manga'
  const provider = animeLike ? 'anilist' : 'tmdb'
  const externalId = animeLike
    ? Number(media?.anilistId || 0)
    : Number(media?.tmdbId || 0)
  if (!Number.isInteger(externalId) || externalId <= 0) return null
  return {
    key:[kind, provider, externalId].join(':'),
    type:kind,
    provider,
    externalId,
  }
}

export function libraryStatusLine(ctx, type, media = {}) {
  const identity = canonicalLibraryIdentity(type, media)
  if (!identity || typeof ctx?.libraryGet !== 'function') return ''
  const found = ctx.libraryGet(identity.key)
  if (!found) return ''
  return found.watchReleases
    ? '✓ In Library · 🔔 Watching releases'
    : '✓ In Library'
}

export function addToLibraryAction(ctx, type, media = {}, { prefix = '.' } = {}) {
  const identity = canonicalLibraryIdentity(type, media)
  if (!identity) return null
  if (typeof ctx?.libraryGet === 'function' && ctx.libraryGet(identity.key)) return null
  return {
    title:'Add to Library',
    id:String(prefix || '.') + COMMANDS[identity.type] + ' ~library-add ' + identity.externalId,
  }
}

function titleOf(media = {}) {
  return String(media?.title || media?.name || media?.aliases?.[0] || 'Untitled').trim() || 'Untitled'
}

function descriptionOf(media = {}) {
  return String(
    media?.overview ||
    media?.description ||
    media?.format ||
    media?.status ||
    media?.year ||
    media?.seasonYear ||
    ''
  ).replace(/\s+/g, ' ').trim().slice(0, 500)
}

function compactMetadata(media = {}) {
  return {
    aliases:Array.isArray(media?.aliases) ? media.aliases.slice(0, 12).map(String) : [],
    year:Number(media?.year || media?.seasonYear || 0) || 0,
    format:String(media?.format || ''),
    status:String(media?.status || ''),
    episodes:Number(media?.episodes || media?.numberOfEpisodes || 0) || 0,
    chapters:Number(media?.chapters || 0) || 0,
    volumes:Number(media?.volumes || 0) || 0,
    seasons:Number(media?.numberOfSeasons || 0) || 0,
    posterPath:String(media?.posterPath || ''),
    siteUrl:String(media?.siteUrl || ''),
  }
}

export async function addCanonicalLibraryItem(ctx, type, externalId) {
  const kind = normalizeLibraryType(type)
  const id = Number(externalId)
  if (!kind || !Number.isInteger(id) || id <= 0) {
    return { ok:false, reason:'invalid' }
  }

  let media = null
  if (kind === 'anime' || kind === 'manga') {
    if (typeof ctx?.resolveAniListMedia !== 'function') return { ok:false, reason:'unavailable' }
    media = await ctx.resolveAniListMedia(id, kind === 'manga' ? 'MANGA' : 'ANIME')
    if (media) media = { ...media, anilistId:id }
  } else {
    if (typeof ctx?.resolveTmdbMedia !== 'function') return { ok:false, reason:'unavailable' }
    media = await ctx.resolveTmdbMedia(id, kind === 'tv' ? 'tv' : 'movie')
    if (media) media = { ...media, tmdbId:id }
  }

  if (!media) return { ok:false, reason:'missing' }
  const identity = canonicalLibraryIdentity(kind, media)
  if (!identity) return { ok:false, reason:'invalid' }

  const existing = typeof ctx?.libraryGet === 'function'
    ? ctx.libraryGet(identity.key)
    : null
  if (existing) return { ok:true, added:false, item:existing }

  const record = {
    itemKey:identity.key,
    mediaType:identity.type,
    provider:identity.provider,
    externalId:String(identity.externalId),
    title:titleOf(media),
    subtitle:descriptionOf(media),
    metadata:compactMetadata(media),
  }
  const stored = typeof ctx?.libraryPut === 'function'
    ? ctx.libraryPut(record)
    : null

  return stored
    ? { ok:true, added:true, item:stored }
    : { ok:false, reason:'storage' }
}
