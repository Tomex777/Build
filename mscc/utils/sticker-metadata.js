import { randomBytes } from 'node:crypto'
import webpMux from 'node-webpmux'

export const DEFAULT_STICKER_PACK_NAME = 'MSCC'
export const DEFAULT_STICKER_PUBLISHER = '『N I G H T』'
export const DEFAULT_STICKER_EMOJIS = ['✨']

function cleanText(value, fallback) {
  const text = String(value ?? '').trim()
  return text || fallback
}

export function stickerPackNameForCommand(ctx, fallback = DEFAULT_STICKER_PACK_NAME) {
  const candidates = [
    ctx?.senderName,
    ctx?.message?.pushName,
    ctx?.userName,
    ctx?.userKey,
  ]
  for (const value of candidates) {
    const text = String(value ?? '').trim()
    if (text) return text.slice(0, 128)
  }
  return fallback
}

function cleanEmojis(value) {
  const list = Array.isArray(value)
    ? value.map(item => String(item ?? '').trim()).filter(Boolean)
    : []
  return list.length ? list : [...DEFAULT_STICKER_EMOJIS]
}

export function stickerMetadata({
  packName = DEFAULT_STICKER_PACK_NAME,
  publisher = DEFAULT_STICKER_PUBLISHER,
  emojis = DEFAULT_STICKER_EMOJIS,
  packId,
} = {}) {
  return {
    'sticker-pack-id': cleanText(packId, randomBytes(32).toString('hex')),
    'sticker-pack-name': cleanText(packName, DEFAULT_STICKER_PACK_NAME),
    'sticker-pack-publisher': cleanText(publisher, DEFAULT_STICKER_PUBLISHER),
    emojis: cleanEmojis(emojis),
  }
}

export function buildStickerExif(options = {}) {
  const metadata = stickerMetadata(options)
  const header = Buffer.from([
    0x49, 0x49, 0x2a, 0x00, 0x08, 0x00, 0x00, 0x00,
    0x01, 0x00, 0x41, 0x57, 0x07, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x16, 0x00, 0x00, 0x00,
  ])
  const json = Buffer.from(JSON.stringify(metadata), 'utf8')
  const exif = Buffer.concat([header, json])
  exif.writeUIntLE(json.length, 14, 4)
  return exif
}

export async function applyStickerMetadata(buffer, options = {}) {
  if (!Buffer.isBuffer(buffer) || !buffer.length) {
    throw new Error('Sticker data is empty.')
  }

  const image = new webpMux.Image()
  await image.load(buffer)
  image.exif = buildStickerExif(options)
  return image.save(null)
}
