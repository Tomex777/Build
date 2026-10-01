import { searchPinterestImages } from '../../utils/pinterest-search.js'
import { downloadImageUrl } from '../../utils/remote-image.js'

const DEFAULT_COUNT = 6
const MAX_COUNT = 20

function parseArgs(args = []) {
  const parts = args.map(value => String(value || '').trim()).filter(Boolean)
  let count = DEFAULT_COUNT

  if (parts.length && /^\d{1,3}$/.test(parts[parts.length - 1])) {
    count = Math.max(1, Math.min(MAX_COUNT, Number(parts.pop()) || DEFAULT_COUNT))
  }

  return {
    query:parts.join(' ').trim(),
    count,
  }
}

export default {
  name:'pin',
  aliases:['pinterest'],
  description:'Search Pinterest and send the matching images.',
  usage:'.pin <search> [amount]',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    const { query, count } = parseArgs(ctx.args)

    if (!query) {
      await ctx.reply(`Use ${prefix}pin <search> [amount]. Example: ${prefix}pin cat 6`)
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
      for (const item of candidates) {
        if (sent >= count) break
        try {
          const image = await downloadImageUrl(item.imageUrl)
          await sock.sendMessage(chat, { image:image.buffer }, { quoted:sent === 0 ? ctx.message : undefined })
          sent += 1
        } catch (error) {
          console.warn('MSCC Pinterest image skipped:', error?.message || error)
        }
      }

      if (!sent) {
        await ctx.reply('Pinterest returned results, but I could not download any of the images.')
      } else if (sent < count) {
        await ctx.reply(`I found ${sent} usable image${sent === 1 ? '' : 's'} for that search.`)
      }
    } catch (error) {
      console.error('MSCC Pinterest search failed:', error)
      await ctx.reply(error?.message || 'Pinterest search failed.')
    }
  },
}
