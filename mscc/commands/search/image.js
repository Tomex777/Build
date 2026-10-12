import { searchImages } from '../../utils/web-search.js'
import { downloadImageUrl } from '../../utils/remote-image.js'
import {
  parseSearchImageArgs,
  fetchQualityImage,
  qualityImagePayload,
} from '../../utils/search-image-quality.js'

const DEFAULT_COUNT = 6
const MAX_COUNT = 20

export default {
  name:'image',
  aliases:['img'],
  description:'Find source-resolution web images. Add hd to deliver uncompressed originals as documents.',
  usage:'.image <search> [amount] [hd]',
  async run(ctx) {
    const { query, count, original } = parseSearchImageArgs(ctx.args, {
      defaultCount:DEFAULT_COUNT, maxCount:MAX_COUNT,
    })
    if (!query) return ctx.reply(`Usage: ${ctx.publicPrefix || '.'}image <search> [1-${MAX_COUNT}] [hd]`)

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
    const seen = new Set()
    for (const item of results) {
      if (sent >= count) break

      const image = await fetchQualityImage(item, { download:downloadImageUrl, seen })
      if (!image) continue

      const caption = sent === 0
        ? [
            `🖼️ *${query}*`,
            item.title || '',
            item.pageUrl || '',
          ].filter(Boolean).join('\n')
        : ''

      try {
        await sock.sendMessage(chat, qualityImagePayload(image, {
          original, caption, index:sent + 1,
        }), { quoted:sent === 0 ? ctx.message : undefined })
        sent += 1
      } catch (error) {
        console.warn('MSCC image result skipped:', error?.message || error)
      }
    }

    if (!sent) return ctx.reply('No sufficiently large source images could be delivered. Try a different search.')
    if (sent < count) return ctx.reply(`I found ${sent} usable high-resolution image${sent === 1 ? '' : 's'} for that search.`)
    return true
  },
}
