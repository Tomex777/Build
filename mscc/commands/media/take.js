import { downloadCommandMedia } from '../../utils/media-conversion.js'
import {
  applyStickerMetadata,
  stickerPackNameForCommand,
} from '../../utils/sticker-metadata.js'

export default {
  name: 'take',
  description: 'Rewrite a sticker pack name to your WhatsApp name.',
  usage: '.take',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    try {
      const source = await downloadCommandMedia(ctx, ['sticker'])
      if (!source) {
        await ctx.reply(`Reply to a sticker with ${prefix}take.`)
        return
      }

      const sticker = await applyStickerMetadata(source.buffer, {
        packName: stickerPackNameForCommand(ctx),
      })

      const chat = ctx.message?.key?.remoteJid
      if (!chat || !ctx.account?.sock) throw new Error('WhatsApp connection is unavailable.')

      await ctx.account.sock.sendMessage(chat, { sticker }, { quoted: ctx.message })
    } catch (error) {
      console.error('MSCC take sticker metadata failed:', error)
      await ctx.reply(error?.message || 'I could not update that sticker.')
    }
  },
}
