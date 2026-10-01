import { downloadCommandMedia, stickerToGif } from '../../utils/media-conversion.js'

export default {
  name: 'togif',
  description: 'Turn a WhatsApp sticker into a real GIF file.',
  usage: '.togif',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    try {
      const source = await downloadCommandMedia(ctx, ['sticker'])
      if (!source) {
        await ctx.reply(`Reply to a sticker with ${prefix}togif.`)
        return
      }

      const gif = await stickerToGif(source.buffer)
      const chat = ctx.message?.key?.remoteJid
      if (!chat || !ctx.account?.sock) throw new Error('WhatsApp connection is unavailable.')

      await ctx.account.sock.sendMessage(chat, {
        document: gif,
        mimetype: 'image/gif',
        fileName: 'sticker.gif',
      }, { quoted: ctx.message })
    } catch (error) {
      console.error('MSCC sticker-to-GIF conversion failed:', error)
      await ctx.reply(error?.message || 'I could not turn that sticker into a GIF.')
    }
  },
}
