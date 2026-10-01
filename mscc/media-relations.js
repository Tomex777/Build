const RELATION_PRIORITY = {
  SOURCE:0,
  ADAPTATION:1,
  PREQUEL:2,
  SEQUEL:3,
  ALTERNATIVE:4,
  SPIN_OFF:5,
  SIDE_STORY:6,
  PARENT:7,
  OTHER:8,
}

function relationRank(value) {
  return RELATION_PRIORITY[String(value || '').toUpperCase()] ?? 99
}

function targetType(fromType) {
  return String(fromType || '').toUpperCase() === 'MANGA' ? 'ANIME' : 'MANGA'
}

function readableFormat(format) {
  const value = String(format || '').toUpperCase()
  if (value === 'MANGA') return 'Manga'
  if (value === 'ONE_SHOT') return 'One-shot'
  if (value === 'NOVEL') return 'Light novel'
  if (value === 'MOVIE') return 'Movie'
  if (value === 'TV') return 'TV anime'
  if (value === 'ONA') return 'ONA'
  if (value === 'OVA') return 'OVA'
  if (value === 'SPECIAL') return 'Special'
  return value ? value.replaceAll('_', ' ').toLowerCase() : 'Related'
}

export function counterpartRelations(media, fromType = '') {
  const wanted = targetType(fromType || media?.type)
  return (media?.relations || [])
    .filter(edge => String(edge?.node?.type || '').toUpperCase() === wanted)
    .sort((a, b) => relationRank(a.relationType) - relationRank(b.relationType))
}

export function counterpartInstantRows(media, {
  fromType = '',
  prefix = '.',
  max = 3,
} = {}) {
  const sourceType = String(fromType || media?.type || '').toUpperCase() || 'ANIME'
  const destinationType = targetType(sourceType)
  const command = destinationType === 'MANGA' ? 'manga' : 'anime'
  const icon = destinationType === 'MANGA' ? '📖' : '▶️'

  return counterpartRelations(media, sourceType)
    .slice(0, Math.max(1, Number(max) || 3))
    .map(edge => {
      const node = edge.node
      const relation = String(edge.relationType || 'RELATED').replaceAll('_', ' ').toLowerCase()
      const format = readableFormat(node.format)
      return {
        title:`${icon} ${format}: ${node.title || 'Related title'}`,
        description:`${relation} · open with ${prefix}${command}`,
        id:`${prefix}${command} ~anilist ${node.id}`,
        mediaId:node.id,
        mediaType:destinationType,
        relationType:String(edge.relationType || ''),
      }
    })
}
