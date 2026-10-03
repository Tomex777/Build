import { searchImages } from '../../utils/web-search.js'
import { downloadImageUrl } from '../../utils/remote-image.js'

const DEFAULT_COUNT = 6
const MAX_COUNT = 20

function parseArgs(args = []) {
  const parts = args.map(value => String(value || '').trim()).filter(Boolean)
  let count = DEFAULT_COUNT
  if (parts.length && /^\d{1,2}$/.test(parts[parts.length - 1])) {
    count = Math.max(1, Math.min(MAX_COUNT, Number(parts.pop()) || DEFAULT_COUNT))
  }
  return { query:parts.join(' ').trim(), count }
}

export default {
  name:'image',
  aliases:['img'],
  description:'Search the web for images and send the matching pictures.',
  usage:'.image <search> [amount]',
  async run(ctx) {
    const { query, count } = parseArgs(ctx.args)
    if (!query) return ctx.reply(`Usage: ${ctx.publicPrefix || '.'}image <search> [1-${MAX_COUNT}]`)

    let results
    try {
      results = await searchImages(query, { limit:Math.min(30, count * 3) })
    } catch (error) {
      console.error('MSCC image search failed:', error)
      return ctx.reply(error?.message || 'Image search failed.')
    }

    if (!results.length) return ctx.reply(`No images found for “${query}”.`)

    const chat = ctx.message?.key?.remoteJid
    const sock = ctx.account?.sock
    if (!chat || !sock) return ctx.reply('WhatsApp connection is unavailable.')

    let sent = 0
    for (const item of results) {
      if (sent >= count) break

      const candidates = [item.imageUrl, item.thumbnailUrl].filter(Boolean)
      let image = null
      for (const url of candidates) {
        try {
          image = await downloadImageUrl(url)
          if (image?.buffer?.length) break
        } catch {}
      }
      if (!image?.buffer?.length) continue

      const caption = sent === 0
        ? [
            `🖼️ *${query}*`,
            item.title || '',
            item.pageUrl || '',
          ].filter(Boolean).join('\n')
        : ''

      try {
        await sock.sendMessage(chat, {
          image:image.buffer,
          ...(caption ? { caption } : {}),
        }, { quoted:sent === 0 ? ctx.message : undefined })
        sent += 1
      } catch (error) {
        console.warn('MSCC image result skipped:', error?.message || error)
      }
    }

    if (!sent) return ctx.reply('I found image results, but none of them could be delivered.')
    if (sent < count) return ctx.reply(`I found ${sent} usable image${sent === 1 ? '' : 's'} for that search.`)
    return true
  },
}
