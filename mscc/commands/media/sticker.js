import { downloadCommandMedia, mediaToSticker } from '../../utils/media-conversion.js'
import { stickerPackNameForCommand } from '../../utils/sticker-metadata.js'

export default {
  name: 'sticker',
  aliases: ['s'],
  description: 'Turn an image, GIF, or video into a WhatsApp sticker.',
  usage: '.sticker',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    try {
      const source = await downloadCommandMedia(ctx, ['image', 'video'])
      if (!source) {
        await ctx.reply(`Reply to an image, GIF, or video with ${prefix}sticker, or send one with ${prefix}sticker as its caption.`)
        return
      }

      const sticker = await mediaToSticker(source.buffer, {
        animated: source.animated,
        packName: stickerPackNameForCommand(ctx),
      })
      const chat = ctx.message?.key?.remoteJid
      if (!chat || !ctx.account?.sock) throw new Error('WhatsApp connection is unavailable.')

      await ctx.account.sock.sendMessage(chat, { sticker }, { quoted: ctx.message })
    } catch (error) {
      console.error('MSCC sticker conversion failed:', error)
      await ctx.reply(error?.message || 'I could not create that sticker.')
    }
  },
}
