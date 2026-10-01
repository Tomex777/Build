import { downloadCommandMedia, stickerToPng } from '../../utils/media-conversion.js'

export default {
  name: 'toimg',
  aliases: ['toimage', 'simage'],
  description: 'Turn a static WhatsApp sticker back into a PNG image.',
  usage: '.toimg',
  async run(ctx) {
    const prefix = String(ctx.publicPrefix || '.')
    try {
      const source = await downloadCommandMedia(ctx, ['sticker'])
      if (!source) {
        await ctx.reply(`Reply to a sticker with ${prefix}toimg.`)
        return
      }
      if (source.animated) {
        await ctx.reply(`That sticker is animated. Use ${prefix}togif for a real GIF or ${prefix}tovideo for MP4.`)
        return
      }

      const image = await stickerToPng(source.buffer)
      const chat = ctx.message?.key?.remoteJid
      if (!chat || !ctx.account?.sock) throw new Error('WhatsApp connection is unavailable.')

      await ctx.account.sock.sendMessage(chat, {
        image,
        mimetype: 'image/png',
      }, { quoted: ctx.message })
    } catch (error) {
      console.error('MSCC sticker-to-image conversion failed:', error)
      await ctx.reply(error?.message || 'I could not turn that sticker into an image.')
    }
  },
}
