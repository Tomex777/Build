import { DEFAULT_STICKER_PUBLISHER } from './sticker-metadata.js'

export const NATIVE_STICKER_PACK_MAX = 50

export function splitStickerPackBuffers(buffers, maxPerPack = NATIVE_STICKER_PACK_MAX) {
  const source = Array.isArray(buffers) ? buffers.filter(Buffer.isBuffer) : []
  const max = Math.max(3, Number(maxPerPack) || NATIVE_STICKER_PACK_MAX)
  if (source.length <= max) return source.length ? [source] : []

  const packCount = Math.ceil(source.length / max)
  const baseSize = Math.floor(source.length / packCount)
  const remainder = source.length % packCount
  const chunks = []
  let offset = 0

  for (let index = 0; index < packCount; index += 1) {
    const size = baseSize + (index < remainder ? 1 : 0)
    chunks.push(source.slice(offset, offset + size))
    offset += size
  }

  return chunks.filter(chunk => chunk.length)
}

export async function sendNativeStickerPacks({
  sock,
  chat,
  stickers,
  packName,
  publisher = DEFAULT_STICKER_PUBLISHER,
} = {}) {
  if (!sock || !chat) throw new Error('WhatsApp connection is unavailable.')
  const name = String(packName || '').trim() || 'Stickers'
  const author = String(publisher || '').trim() || DEFAULT_STICKER_PUBLISHER
  const chunks = splitStickerPackBuffers(stickers)

  for (const chunk of chunks) {
    if (chunk.length < 3) {
      for (const sticker of chunk) {
        await sock.sendMessage(chat, { sticker })
      }
      continue
    }

    await sock.sendMessage(chat, {
      cover: chunk[0],
      stickers: chunk.map(data => ({ data })),
      name,
      publisher: author,
      description: `Created by ${author}`,
    })
  }

  return chunks.map(chunk => chunk.length)
}
