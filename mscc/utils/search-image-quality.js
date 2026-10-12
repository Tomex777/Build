import { createHash } from 'node:crypto'

// WhatsApp recompresses ordinary photos. HD mode sends the original bytes
// as a document; regular mode keeps the inline image experience but must not
// silently substitute a tiny search-result thumbnail.
export const SEARCH_IMAGE_MIN_SHORT_EDGE = 600
export const SEARCH_IMAGE_MIN_LONG_EDGE = 900

export function parseSearchImageArgs(args = [], { defaultCount = 6, maxCount = 20 } = {}) {
  const parts = (Array.isArray(args) ? args : String(args || '').split(/\s+/))
    .map(value => String(value || '').trim()).filter(Boolean)
  let count = defaultCount
  let original = false
  // Accept either "<query> 6 hd" or "<query> hd 6", while treating "hd"
  // earlier in the actual query as an ordinary search term.
  for (let i = 0; i < 2 && parts.length; i++) {
    const last = parts.at(-1)
    if (/^(?:--hd|--original|--document|hd)$/i.test(last)) {
      original = true
      parts.pop()
      continue
    }
    if (/^\d{1,3}$/.test(last)) {
      count = Math.max(1, Math.min(maxCount, Number(last) || defaultCount))
      parts.pop()
      continue
    }
    break
  }
  return { query:parts.join(' ').trim(), count, original }
}

function sizeFromJpeg(buffer) {
  // JPEG SOF (Start Of Frame) markers store the actual encoded dimensions.
  // Skip APP / EXIF / ICC / entropy data; don't infer size from a URL.
  let offset = 2
  while (offset + 9 < buffer.length) {
    if (buffer[offset] !== 0xff) break
    while (offset < buffer.length && buffer[offset] === 0xff) offset++
    const marker = buffer[offset++]
    if (marker === 0xda || marker === 0xd9) break
    if (marker === 0x01 || marker >= 0xd0 && marker <= 0xd7) continue
    if (offset + 2 > buffer.length) break
    const length = buffer.readUInt16BE(offset)
    if (length < 2 || offset + length > buffer.length) break
    if ([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf].includes(marker)) {
      if (length < 7) break
      return { width:buffer.readUInt16BE(offset + 5), height:buffer.readUInt16BE(offset + 3), extension:'jpg', mimetype:'image/jpeg' }
    }
    offset += length
  }
  return null
}

export function imageBufferDetails(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 24) return null
  if (buffer.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) &&
      buffer.toString('ascii', 12, 16) === 'IHDR') {
    return { width:buffer.readUInt32BE(16), height:buffer.readUInt32BE(20), extension:'png', mimetype:'image/png' }
  }
  if (buffer[0] === 0xff && buffer[1] === 0xd8) return sizeFromJpeg(buffer)
  if (['GIF87a','GIF89a'].includes(buffer.toString('ascii', 0, 6))) {
    return { width:buffer.readUInt16LE(6), height:buffer.readUInt16LE(8), extension:'gif', mimetype:'image/gif' }
  }
  if (buffer.toString('ascii', 0, 4) === 'RIFF' && buffer.toString('ascii', 8, 12) === 'WEBP') {
    const chunk = buffer.toString('ascii', 12, 16)
    if (chunk === 'VP8X' && buffer.length >= 30) {
      return {
        width:1 + buffer.readUIntLE(24, 3), height:1 + buffer.readUIntLE(27, 3),
        extension:'webp', mimetype:'image/webp',
      }
    }
    if (chunk === 'VP8L' && buffer.length >= 25 && buffer[20] === 0x2f) {
      const bits = buffer.readUInt32LE(21)
      return {
        width:(bits & 0x3fff) + 1, height:((bits >>> 14) & 0x3fff) + 1,
        extension:'webp', mimetype:'image/webp',
      }
    }
    if (chunk === 'VP8 ' && buffer.length >= 30 &&
        buffer[23] === 0x9d && buffer[24] === 0x01 && buffer[25] === 0x2a) {
      return {
        width:buffer.readUInt16LE(26) & 0x3fff, height:buffer.readUInt16LE(28) & 0x3fff,
        extension:'webp', mimetype:'image/webp',
      }
    }
  }
  return null
}

export function isUsableSearchImage(details) {
  if (!details || !Number.isInteger(details.width) || !Number.isInteger(details.height)) return false
  const short = Math.min(details.width, details.height)
  const long = Math.max(details.width, details.height)
  return short >= SEARCH_IMAGE_MIN_SHORT_EDGE && long >= SEARCH_IMAGE_MIN_LONG_EDGE
}

function originalPinimgUrl(sourceUrl) {
  try {
    const url = new URL(sourceUrl)
    if (url.protocol !== 'https:' || !/(^|\.)pinimg\.com$/i.test(url.hostname)) return ''
    const match = url.pathname.match(/^\/(?:\d+x)\/(.+\.(?:jpe?g|png|webp))$/i)
    if (!match) return ''
    url.pathname = '/originals/' + match[1]
    return url.href
  } catch { return '' }
}

export function sourceImageUrls(item) {
  // Don't include Bing thumbnailUrl: those small previews are exactly what
  // used to be sent when the actual source image was unavailable.
  const candidates = Array.isArray(item?.imageUrls) ? item.imageUrls : []
  const known = [item?.imageUrl, ...candidates].filter(x => typeof x === 'string' && /^https?:\/\//i.test(x))
  const urls = []
  const seen = new Set()
  for (const url of known) {
    const maybeOriginal = originalPinimgUrl(url)
    for (const candidate of [maybeOriginal, url].filter(Boolean)) {
      if (!seen.has(candidate)) {
        seen.add(candidate)
        urls.push(candidate)
      }
    }
  }
  return urls
}

export async function fetchQualityImage(item, { download, seen = new Set() } = {}) {
  if (typeof download !== 'function') throw new Error('Image downloader is required')
  for (const url of sourceImageUrls(item)) {
    try {
      const response = await download(url)
      const details = imageBufferDetails(response?.buffer)
      if (!isUsableSearchImage(details)) continue
      const digest = createHash('sha256').update(response.buffer).digest('hex')
      if (seen.has(digest)) return null
      seen.add(digest)
      return { buffer:response.buffer, details, url }
    } catch {
      // A source may block hotlinking. Try other high-resolution variants or
      // move to the next result; never quietly send a preview thumbnail.
    }
  }
  return null
}

export function qualityImagePayload(image, {
  original = false, caption = '', index = 1,
} = {}) {
  if (!image?.buffer?.length || !image.details) throw new Error('Invalid image')
  if (original) {
    return {
      document:image.buffer,
      mimetype:image.details.mimetype,
      fileName:`MSCC-image-${index}.${image.details.extension}`,
      ...(caption ? { caption } : {}),
    }
  }
  return { image:image.buffer, ...(caption ? { caption } : {}) }
}
