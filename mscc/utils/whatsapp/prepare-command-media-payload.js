import { readFile, stat } from 'node:fs/promises'

// Large media must stay as a local file URL; Baileys streams local paths.
// Buffering a feature-length video can exceed the bot's 192 MiB heap/container budget.
const MAX_BUFFERED_LOCAL_MEDIA_BYTES = 8 * 1024 * 1024

export async function prepareCommandMediaPayload(payload) {
  if (!payload || typeof payload !== 'object') return payload
  const next = { ...payload }
  for (const kind of ['image', 'video', 'audio', 'document', 'sticker']) {
    const media = next[kind]
    if (!media || typeof media !== 'object' || typeof media.url !== 'string') continue
    const url = String(media.url)
    if (!url.startsWith('/')) continue
    try {
      const file = await stat(url)
      // Preserve Buffer behavior for small files, but stream large files.
      if (file.size > MAX_BUFFERED_LOCAL_MEDIA_BYTES) continue
      next[kind] = await readFile(url)
    } catch (error) {
      throw new Error('Unable to read local ' + kind + ' media: ' + (error?.message || error))
    }
  }
  return next
}

export const _test = { MAX_BUFFERED_LOCAL_MEDIA_BYTES }
