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
