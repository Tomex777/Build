const MIME_TO_EXTENSION = new Map([
  ['image/jpeg', 'jpg'],
  ['image/png', 'png'],
  ['image/webp', 'webp'],
  ['image/gif', 'gif'],
  ['video/mp4', 'mp4'],
  ['video/webm', 'webm'],
  ['audio/mpeg', 'mp3'],
  ['audio/mp4', 'm4a'],
  ['audio/ogg', 'ogg'],
  ['application/pdf', 'pdf'],
  ['application/zip', 'zip'],
])

export const extensionForMime = mime =>
  MIME_TO_EXTENSION.get(String(mime || '').toLowerCase()) || ''

export function mediaKindFromMime(mime) {
  const value = String(mime || '').toLowerCase()
  if (value.startsWith('image/')) return value === 'image/webp' ? 'sticker' : 'image'
  if (value.startsWith('video/')) return 'video'
  if (value.startsWith('audio/')) return 'audio'
  return 'document'
}

export function normalizeMediaSource(source) {
  if (Buffer.isBuffer(source) || source instanceof Uint8Array) return source
  if (typeof source === 'string') return { url: source }
  if (source && typeof source === 'object') return source
  throw new Error('Unsupported media source')
}

export function buildMediaPayload({
  source,
  kind = '',
  mimetype = '',
  fileName = '',
  caption = '',
  ptt = false,
  gifPlayback = false,
} = {}) {
  const resolvedKind = kind || mediaKindFromMime(mimetype)
  const media = normalizeMediaSource(source)
  const payload = {}

  if (resolvedKind === 'image') payload.image = media
  else if (resolvedKind === 'video') payload.video = media
  else if (resolvedKind === 'audio') payload.audio = media
  else if (resolvedKind === 'sticker') payload.sticker = media
  else payload.document = media

  if (mimetype) payload.mimetype = String(mimetype)
  if (fileName) payload.fileName = String(fileName)
  if (caption && resolvedKind !== 'audio' && resolvedKind !== 'sticker') payload.caption = String(caption)
  if (resolvedKind === 'audio' && ptt) payload.ptt = true
  if (resolvedKind === 'video' && gifPlayback) payload.gifPlayback = true

  return payload
}
