import { searchPinterestImages } from '../../utils/pinterest-search.js'
import { downloadImageUrl } from '../../utils/remote-image.js'
import {
  parseSearchImageArgs,
  fetchQualityImage,
  qualityImagePayload,
} from '../../utils/search-image-quality.js'

const DEFAULT_COUNT = 6
const MAX_COUNT = 20

export default {
  name:'pin',
  aliases:['pinterest'],
  description:'Search Pinterest for full-size images. Add hd to send original files without WhatsApp recompression.',
  usage:'.pin <search> [amount] [hd]',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    const { query, count, original } = parseSearchImageArgs(ctx.args, {
      defaultCount:DEFAULT_COUNT, maxCount:MAX_COUNT,
    })
    if (!query) {
      await ctx.reply(`Use ${prefix}pin <search> [amount] [hd]. Example: ${prefix}pin cat 6 hd`)
      return
    }

    try {
      const candidates = await searchPinterestImages(query, {
        limit:count,
        candidateMultiplier:3,
      })

      if (!candidates.length) {
        await ctx.reply('I could not find Pinterest images for that search.')
        return
      }

      const chat = ctx.message?.key?.remoteJid
      const sock = ctx.account?.sock
      if (!chat || !sock) throw new Error('WhatsApp connection is unavailable.')

      let sent = 0
      const seen = new Set()
      for (const item of candidates) {
        if (sent >= count) break
        try {
          const image = await fetchQualityImage(item, { download:downloadImageUrl, seen })
          if (!image) continue
          await sock.sendMessage(chat, qualityImagePayload(image, {
            original, index:sent + 1,
          }), { quoted:sent === 0 ? ctx.message : undefined })
          sent += 1
        } catch (error) {
          console.warn('MSCC Pinterest image skipped:', error?.message || error)
        }
      }

      if (!sent) {
        await ctx.reply('Pinterest returned no sufficiently large downloadable images. Try another search.')
      } else if (sent < count) {
        await ctx.reply(`I found ${sent} usable high-resolution image${sent === 1 ? '' : 's'} for that search.`)
      }
    } catch (error) {
      console.error('MSCC Pinterest search failed:', error)
      await ctx.reply(error?.message || 'Pinterest search failed.')
    }
  },
}
